package com.zyvioip.app.repository

import com.zyvioip.app.model.IpSignals
import com.zyvioip.app.model.mergeSignals
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderOutcome
import com.zyvioip.app.provider.ProviderStatus
import com.zyvioip.app.ui.HomeRepositoryApi
import com.zyvioip.app.ui.HomeSettings
import com.zyvioip.app.util.averagePurity
import com.zyvioip.app.util.classifyIpType
import com.zyvioip.app.util.isValidIp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates local IP discovery, concurrent provider queries, TTL caching and aggregation. */
class IpLookupRepository(
    private val cache: CacheRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val localIpRepository: LocalIpRepository = LocalIpRepository(),
) : HomeRepositoryApi {

    private val _providerStates = MutableStateFlow<List<ProviderOutcome>>(emptyList())
    private val _aggregate = MutableStateFlow<com.zyvioip.app.model.AggregatedResult?>(null)
    private val _localIp = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _homeSettings = MutableStateFlow(HomeSettings())
    private val mutex = Mutex()
    private var settings = LookupSettings()
    private var currentIp: String? = null

    override val providerStates: StateFlow<List<ProviderOutcome>> = _providerStates.asStateFlow()
    override val aggregate: StateFlow<com.zyvioip.app.model.AggregatedResult?> = _aggregate.asStateFlow()
    override val localIp: StateFlow<String?> = _localIp.asStateFlow()
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()
    override val error: StateFlow<String?> = _error.asStateFlow()
    override val homeSettings: StateFlow<HomeSettings> = _homeSettings.asStateFlow()

    override suspend fun initialize() {
        settings = settingsRepository?.load() ?: settings
        // 把持久化的设置回填给 UI，否则设置页永远显示编译期默认值（改了开关回来又变回去）。
        _homeSettings.value = HomeSettings(
            timeoutMs = settings.timeoutMs,
            cacheTtlMinutes = settings.cacheTtlMs / 60_000L,
            autoLookup = settings.autoLookup,
            showFlags = settings.showFlags,
            notifications = settings.notifications,
        )
        val cached = cache.getLocalIp(LOCAL_CACHE_KEY, LocalIpRepository.LOCAL_CACHE_TTL_MS)?.takeIf(::isValidIp)
        val detected = cached ?: localIpRepository.detect()
        if (detected != null) {
            _localIp.value = detected
            if (cached != detected) cache.putLocalIp(LOCAL_CACHE_KEY, detected)
        }
        currentIp = _localIp.value
        _providerStates.value = settings.activeIds.mapNotNull { id ->
            ProviderCatalog.byId(id)?.let { ProviderOutcome(id, ProviderStatus.PENDING) }
        }
        // 尊重“启动时自动查询本机 IP”开关：关掉时只显示本机 IP，不发任何请求。
        if (settings.autoLookup) currentIp?.let { search(it) }
    }

    override suspend fun search(ip: String) {
        val cleanIp = ip.trim()
        if (!isValidIp(cleanIp)) {
            _error.value = "IP 格式不正确"
            return
        }
        currentIp = cleanIp
        _error.value = null
        _loading.value = true
        val active = settings.activeIds.mapNotNull(ProviderCatalog::byId)
        _providerStates.value = active.map { ProviderOutcome(it.id, ProviderStatus.LOADING) }
        try {
            val results = coroutineScope {
                active.map { spec -> async { query(spec.id, cleanIp, bypassCache = false) } }.awaitAll()
            }
            _providerStates.value = results
            rebuild(cleanIp, results)
        } finally {
            _loading.value = false
        }
    }

    override suspend fun retry(providerId: String) {
        val spec = ProviderCatalog.byId(providerId) ?: return
        val ip = currentIp ?: return
        val result = query(spec.id, ip, bypassCache = true)
        val next = _providerStates.value.map { if (it.providerId == spec.id) result else it }
        _providerStates.value = if (next.any { it.providerId == spec.id }) next else next + result
        rebuild(ip, next)
    }

    override suspend fun removeProvider(providerId: String) {
        settings = settings.copy(activeIds = settings.activeIds - providerId)
        settingsRepository?.save(settings)
        val next = _providerStates.value.filterNot { it.providerId == providerId }
        _providerStates.value = next
        currentIp?.let { rebuild(it, next) }
    }

    override suspend fun addProvider(providerId: String) {
        val spec = ProviderCatalog.byId(providerId) ?: return
        settings = settings.copy(activeIds = settings.activeIds + providerId)
        settingsRepository?.save(settings)
        if (_providerStates.value.none { it.providerId == providerId }) {
            // 撤销删除后要立刻重新查询，否则卡片会一直停在 PENDING 状态。
            val result = currentIp?.let { query(spec.id, it, bypassCache = false) }
                ?: ProviderOutcome(spec.id, ProviderStatus.PENDING)
            _providerStates.value = _providerStates.value + result
        }
        currentIp?.let { rebuild(it, _providerStates.value) }
    }

    override suspend fun setConfig(providerId: String, config: ProviderConfig) {
        settings = settings.copy(
            keys = if (config.apiKey.isBlank()) settings.keys - providerId else settings.keys + (providerId to config.apiKey),
            emails = if (config.email.isBlank()) settings.emails - providerId else settings.emails + (providerId to config.email),
        )
        settingsRepository?.save(settings)
        if (settings.activeIds.contains(providerId)) currentIp?.let { search(it) }
    }

    override suspend fun getConfig(providerId: String): ProviderConfig = ProviderConfig(
        providerId = providerId,
        apiKey = settings.keys[providerId].orEmpty(),
        email = settings.emails[providerId].orEmpty(),
    )

    override suspend fun clearCache() {
        cache.clear()
        _localIp.value = null
        _error.value = null
        // 只清缓存，不要顺手把当前结果和本机 IP 也抹掉 ——
        // 原来会把 aggregate 置空，用户点完“清除缓存”看到界面空白，会以为把供应商删了。
        // 现在的行为：清完重新拉一次本机 IP 并刷新结果。
        val detected = runCatching { localIpRepository.detect() }.getOrNull()
        if (detected != null) {
            _localIp.value = detected
            cache.putLocalIp(LOCAL_CACHE_KEY, detected)
            currentIp = detected
            search(detected)
        }
    }

    override suspend fun updateSettings(settings: HomeSettings) {
        this.settings = this.settings.copy(
            timeoutMs = settings.timeoutMs.coerceIn(1_000L, 60_000L),
            cacheTtlMs = settings.cacheTtlMinutes.coerceAtLeast(0L) * 60_000L,
            autoLookup = settings.autoLookup,
            showFlags = settings.showFlags,
            notifications = settings.notifications,
        )
        settingsRepository?.save(this.settings)
        _homeSettings.value = settings
    }

    private suspend fun query(providerId: String, ip: String, bypassCache: Boolean): ProviderOutcome {
        val spec = ProviderCatalog.byId(providerId)
            ?: return ProviderOutcome(providerId, ProviderStatus.ERROR, error = "未知供应商")
        val config = ProviderConfig(providerId, settings.keys[providerId].orEmpty(), settings.emails[providerId].orEmpty())
        if ((spec.requiresKey && !config.hasKey) || (spec.requiresEmail && !config.hasEmail)) {
            return ProviderOutcome(providerId, ProviderStatus.NEEDS_CONFIG, error = "尚未配置密钥或邮箱")
        }
        val key = cacheKey(ip, providerId, config.apiKey, config.email)
        if (!bypassCache) {
            cache.getProviderResult(key, settings.cacheTtlMs)?.let { cached ->
                return cached.asOutcome()
            }
        }
        return mutex.withLock {
            try {
                val outcome = ProviderCatalog.create(spec, config).lookup(ip, settings.timeoutMs)
                if (outcome.status == ProviderStatus.SUCCESS) {
                    cache.putProviderResult(key, com.zyvioip.app.model.ProviderResult(
                        providerId = providerId, status = com.zyvioip.app.model.ProviderStatus.SUCCESS,
                        signals = com.zyvioip.app.model.IpSignals(
                            ip = outcome.ip, country = outcome.country, city = outcome.city,
                            company = outcome.company, asn = outcome.asn, flags = outcome.flags,
                        ),
                        purity = outcome.purity, error = outcome.error,
                    ))
                }
                outcome
            } catch (_: Exception) {
                ProviderOutcome(providerId, ProviderStatus.ERROR, error = "查询失败，请稍后重试")
            }
        }
    }

    private fun rebuild(ip: String, results: List<ProviderOutcome>) {
        var signals = IpSignals(ip = ip)
        val purities = mutableListOf<Int>()
        for (result in results) {
            if (result.status != ProviderStatus.SUCCESS) continue
            signals = mergeSignals(
                signals,
                IpSignals(
                    ip = result.ip ?: ip,
                    country = result.country,
                    countryCode = result.countryCode,
                    city = result.city,
                    region = result.region,
                    company = result.company,
                    asn = result.asn,
                    isVpn = result.flags.any { it.equals("vpn", true) },
                    isProxy = result.flags.any { it.equals("proxy", true) },
                    isTor = result.flags.any { it.equals("tor", true) },
                    isDatacenter = result.flags.any { it.equals("datacenter", true) },
                    isAbuser = result.flags.any { it.equals("abuser", true) },
                    isMobile = result.flags.any { it.equals("mobile", true) },
                    flags = result.flags,
                ),
            )
            val spec = ProviderCatalog.byId(result.providerId)
            if (spec?.scorable == true && result.purity != null) purities.add(result.purity)
        }
        _aggregate.value = com.zyvioip.app.model.AggregatedResult(
            ip = ip,
            signals = signals,
            providerStates = results.mapNotNull { result ->
                ProviderCatalog.byId(result.providerId)?.let { spec ->
                    com.zyvioip.app.model.ProviderState(
                        com.zyvioip.app.model.ProviderSpec(
                            id = spec.id,
                            name = spec.name,
                            auth = when (spec.auth) {
                                com.zyvioip.app.provider.ProviderAuth.EMAIL -> com.zyvioip.app.model.AuthKind.EMAIL
                                com.zyvioip.app.provider.ProviderAuth.API_KEY -> com.zyvioip.app.model.AuthKind.API_KEY
                                else -> com.zyvioip.app.model.AuthKind.NONE
                            },
                            defaultActive = spec.defaultActive,
                            requiresKey = spec.requiresKey,
                            requiresEmail = spec.requiresEmail,
                            scorable = spec.scorable,
                            note = spec.note,
                            configLabel = spec.configLabel,
                        ),
                        result,
                    )
                }
            },
            purity = averagePurity(purities),
            type = classifyIpType(signals),
        )
    }

    private fun cacheKey(ip: String, id: String, key: String, email: String): String =
        listOf(ip, id, key, email).joinToString("|")

    private companion object {
        const val LOCAL_CACHE_KEY = "device"
    }
}
