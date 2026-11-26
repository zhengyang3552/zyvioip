package com.zyvioip.app.repository

import com.zyvioip.app.model.IpSignals
import com.zyvioip.app.model.mergeSignals
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderOutcome
import com.zyvioip.app.provider.ProviderStatus
import com.zyvioip.app.ui.HomeRepositoryApi
import com.zyvioip.app.ui.HomeSettings
import com.zyvioip.app.util.classifyIpType
import com.zyvioip.app.util.isValidIp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

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
    /** 查询代际号：只有最新一次查询允许写回状态，避免启动查询与手动查询互相覆盖。 */
    @Volatile private var searchGeneration = 0L


    /** 当前正在执行的查询任务，新一代查询开始时会取消它。 */
    @Volatile private var activeSearchJob: Job? = null

    /**
     * 保护 _providerStates 的所有读-改-写。
     *
     * addProvider / retry 都是“先挂起网络请求，再基于旧快照写回”，
     * 没有互斥就会重复追加或互相覆盖 —— 重复的 providerId 会让
     * LazyColumn 因重复 key 崩溃。
     */
    private val stateMutex = Mutex()
    private var settings = LookupSettings()
    private var currentIp: String? = null

    override val providerStates: StateFlow<List<ProviderOutcome>> = _providerStates.asStateFlow()
    override val aggregate: StateFlow<com.zyvioip.app.model.AggregatedResult?> = _aggregate.asStateFlow()
    override val localIp: StateFlow<String?> = _localIp.asStateFlow()
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()
    override val error: StateFlow<String?> = _error.asStateFlow()
    override val homeSettings: StateFlow<HomeSettings> = _homeSettings.asStateFlow()

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        // 整个初始化都在 IO 线程：
        // 之前跑在 viewModelScope 默认的 Main 上，settings.load 与
        // cache.preload（全表 JSON 解码）都压在主线程，启动瞬间明显卡顿。
        settings = settingsRepository?.load() ?: settings

        // 把持久化的设置回填给 UI，否则设置页永远显示编译期默认值
        _homeSettings.value = HomeSettings(
            timeoutMs = settings.timeoutMs,
            cacheTtlMinutes = settings.cacheTtlMs / 60_000L,
            autoLookup = settings.autoLookup,
            showFlags = settings.showFlags,
            notifications = settings.notifications,
        )

        // 先把本轮要用的供应商卡片渲染出来（PENDING），别让用户盯着空白页等 IP 探测
        _providerStates.value = settings.activeIds.distinct().mapNotNull { id ->
            ProviderCatalog.byId(id)?.let { ProviderOutcome(id, ProviderStatus.PENDING) }
        }

        // 持久缓存一次性灌进内存，后续查询路径不再碰磁盘
        (cache as? DataStoreCacheRepository)?.preload()

        val cached = cache.getLocalIp(LOCAL_CACHE_KEY, LocalIpRepository.LOCAL_CACHE_TTL_MS)
            ?.takeIf(::isValidIp)
        val detected = cached ?: localIpRepository.detect()
        if (detected != null) {
            _localIp.value = detected
            if (cached != detected) cache.putLocalIp(LOCAL_CACHE_KEY, detected)
        }
        currentIp = _localIp.value

        // 尊重“启动时自动查询本机 IP”开关
        if (settings.autoLookup) currentIp?.let { search(it) }
    }

    override suspend fun refreshLocalIp(forceQuery: Boolean): String? {
        val previous = _localIp.value
        val detected = localIpRepository.detect()
            ?: return previous.also {
                _error.value = "无法获取本机公网 IP，请检查网络后重试"
            }
        _localIp.value = detected
        cache.putLocalIp(LOCAL_CACHE_KEY, detected)
        val changed = detected != previous
        // 切网络后即使探测到的 IP 相同，链路可能已经变了（例如跨运营商），
        // 所以 forceQuery=true 时也重查一次。
        if (changed || forceQuery) {
            currentIp = detected
            search(detected)
        }
        return detected
    }

    override suspend fun search(ip: String) {
        val cleanIp = ip.trim()
        if (!isValidIp(cleanIp)) {
            _error.value = "IP 格式不正确"
            return
        }
        // 新一代查询开始：先取消上一次还没跑完的任务。
        // 之前只靠 generation 丢弃结果，旧任务仍在后台跑完并占着网络，
        // 于是出现「第一次查询失败、第二次就成功」——
        // 第一次其实是在和新旧两批请求一起抢带宽/DNS。
        val generation = ++searchGeneration
        activeSearchJob?.cancel()
        activeSearchJob = currentCoroutineContext().job

        currentIp = cleanIp
        _error.value = null
        _loading.value = true
        // distinctBy 兜底：即使 activeIds 被写入了重复项（历史数据 / 并发添加），
        // 也不能让重复的 providerId 进入列表 —— LazyColumn 用 id 当 key，重复即崩溃。
        val active = settings.activeIds.distinct().mapNotNull(ProviderCatalog::byId)
        stateMutex.withLock {
            _providerStates.value = active.map { ProviderOutcome(it.id, ProviderStatus.LOADING) }
        }

        try {
            // 改为**串行请求**：一个源查完再查下一个。
            //
            // 之前的并发（6 个同时）会带来三个问题：
            //   1. 移动网络下突发 6 条 TLS 连接，首屏握手排队，整体反而更慢
            //   2. 6 份响应几乎同时返回，解析与状态回写集中爆发，主线程被反复打断
            //   3. 部分免费接口有每秒/每分钟限流，并发容易被判定超限
            // 串行后每个请求独占链路，且**每完成一个就立刻更新 UI**，
            // 用户能看到进度逐个点亮，不必等全部完成。
            val collected = mutableListOf<ProviderOutcome>()
            // 串行后最坏耗时 = 源数量 × 单源超时（6 × 8s = 48s），
            // 用户等不了那么久。设一个整体预算：超时的源直接标记为“已跳过”，
            // 已拿到的结果照常展示。
            val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
            for ((index, spec) in active.withIndex()) {
                if (generation != searchGeneration) break
                if (System.currentTimeMillis() > deadline) {
                    collected += ProviderOutcome(
                        spec.id,
                        ProviderStatus.SKIPPED,
                        error = "整体查询已超时，本次跳过",
                    )
                    continue
                }

                // 把“当前这个”标成 LOADING。
                // 只在确实需要变更时才写回 —— 每次都无脑 map 出全新 List 会让
                // LazyColumn 认为整个列表变了，触发一轮完整重组。
                stateMutex.withLock {
                    val current = _providerStates.value
                    val idx = current.indexOfFirst { it.providerId == spec.id }
                    if (idx >= 0 && current[idx].status != ProviderStatus.LOADING) {
                        _providerStates.value = current.toMutableList().also {
                            it[idx] = it[idx].copy(status = ProviderStatus.LOADING)
                        }
                    }
                }

                val outcome = query(spec.id, cleanIp, bypassCache = false)
                collected += outcome

                // 单条完成即回写：逐卡刷新，避免“等全部完成才一起跳变”
                val snapshot = collected.toList()
                stateMutex.withLock {
                    val next = upsert(_providerStates.value, outcome)
                    if (next != _providerStates.value) _providerStates.value = next
                }

                // 汇总只在最后一条、或中途遇到条目变化时才重算，
                // 避免 n 次 withContext(IO) 切换。
                if (index == active.lastIndex || outcome.status != ProviderStatus.SUCCESS) {
                    rebuild(cleanIp, snapshot)
                }
            }
            val results = collected.toList()
            // 期间若已有更新的查询，直接放弃本次结果（状态归新的那次管）
            if (generation != searchGeneration) return
            val merged = stateMutex.withLock {
                // 合并而非整体替换：期间用户可能新增/删除过供应商
                var next = _providerStates.value
                results.forEach { next = upsert(next, it) }
                _providerStates.value = next
                next
            }
            rebuild(cleanIp, merged)
        } catch (e: CancellationException) {
            // 被新一代查询取消：如果此时没有更新的查询在跑（例如页面销毁），
            // 必须把卡片从 LOADING 恢复成待命，否则界面会永远转圈。
            if (generation == searchGeneration) {
                _providerStates.value = _providerStates.value.map {
                    if (it.status == ProviderStatus.LOADING) it.copy(status = ProviderStatus.PENDING) else it
                }
            }
            throw e
        } finally {
            // 只有最新一次查询才有权结束 loading，
            // 否则旧查询被取消时会把新查询的 loading 状态提前清掉。
            if (generation == searchGeneration) _loading.value = false
        }
    }

    override suspend fun retry(providerId: String) {
        val spec = ProviderCatalog.byId(providerId) ?: return
        val ip = currentIp ?: return
        val generation = searchGeneration

        // 先把它标成 LOADING，用户能看到进度而不是一直停在“失败”
        stateMutex.withLock {
            _providerStates.value = upsert(
                _providerStates.value,
                ProviderOutcome(spec.id, ProviderStatus.LOADING),
            )
        }

        val result = query(spec.id, ip, bypassCache = true)

        // 期间如果用户又发起了新的整体查询，就不要用这次单源结果去覆盖它
        if (generation != searchGeneration) return

        val next = stateMutex.withLock {
            val merged = upsert(_providerStates.value, result)
            _providerStates.value = merged
            merged
        }
        rebuild(ip, next)
    }

    override suspend fun removeProvider(providerId: String) {
        settings = settings.copy(activeIds = settings.activeIds - providerId)
        settingsRepository?.save(settings)
        val next = stateMutex.withLock {
            val filtered = _providerStates.value.filterNot { it.providerId == providerId }
            _providerStates.value = filtered
            filtered
        }
        // 锁外做聚合：rebuild 是 suspend（内部切 IO），不应持锁执行
        val ip = currentIp
        if (ip != null) rebuild(ip, next)
    }

    override suspend fun addProvider(providerId: String) {
        val spec = ProviderCatalog.byId(providerId) ?: return

        // 先去重并立刻插入 PENDING，让卡片马上出现 ——
        // 之前这里是“等网络请求返回后才追加”，用户点完添加会卡住几百毫秒到数秒，
        // 期间连点或并发的其它写入还会把同一项追加两次，
        // 最终导致 LazyColumn 出现重复 key 直接崩溃。
        val alreadyPresent = stateMutex.withLock {
            val present = _providerStates.value.any { it.providerId == providerId }
            if (!present) {
                _providerStates.value = upsert(
                    _providerStates.value,
                    ProviderOutcome(spec.id, ProviderStatus.PENDING),
                )
            }
            present
        }

        // 持久化放在后面，不阻塞 UI
        settings = settings.copy(activeIds = settings.activeIds + providerId)
        settingsRepository?.save(settings)

        // 已在列表里就不重复查询
        if (alreadyPresent) return

        val ip = currentIp ?: return
        val generation = searchGeneration
        val result = query(spec.id, ip, bypassCache = false)
        if (generation != searchGeneration) return

        val next = stateMutex.withLock {
            val merged = upsert(_providerStates.value, result)
            _providerStates.value = merged
            merged
        }
        rebuild(ip, next)
    }

    /**
     * 按 providerId 合并：存在则替换，不存在则**追加**。
     *
     * 之前多处直接用 `list + result` 或 `list + result` 式追加，
     * 并发写入时会重复插入同一 id；而 LazyColumn 用 providerId 当 key，
     * 重复 key 会抛 IllegalArgumentException 直接崩溃。
     * 所有写入统一走这里，从根上保证 id 唯一。
     */
    private fun upsert(
        current: List<ProviderOutcome>,
        incoming: ProviderOutcome,
    ): List<ProviderOutcome> {
        val index = current.indexOfFirst { it.providerId == incoming.providerId }
        if (index < 0) return current + incoming
        return current.toMutableList().also { it[index] = incoming }
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
        // 回写“实际生效”的值，而不是用户原始输入：
        // 原来直接回写 settings，用户填 0 或 9999 时界面显示的是非法值，但底层已被夹到 1~60s，
        // 两边不一致会让人以为设置没生效。
        _homeSettings.value = HomeSettings(
            timeoutMs = this.settings.timeoutMs,
            cacheTtlMinutes = this.settings.cacheTtlMs / 60_000L,
            autoLookup = this.settings.autoLookup,
            showFlags = this.settings.showFlags,
            notifications = this.settings.notifications,
        )
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
        // 这里绝不能用全局 mutex 包住整个网络请求：
        // 那会把所有供应商从“并发”变成“串行”，任何一个慢请求都会拖住其余全部。
        // 表现就是「冷启动时全部失败，手动点一下查询又全部成功」——
        // 首次冷 DNS 本来就慢，串行叠加后几乎必然撞上超时。
        // 并发安全改由「状态写回时按 providerId 合并」保证（见 search / retry）。
        // 整段网络请求 + JSON 解析 + 缓存写入全部放到 IO 线程。
        // 之前 query() 在 search() 的协程内直接调用，而 search() 跑在
        // viewModelScope 的 Main 上 —— 6 个源并发时，6 份响应体的 JSON 解析
        // （ipto.to 单条 44 个字段）全挤在主线程，滚动和首屏都明显掉帧。
        return try {
            val outcome = withContext(Dispatchers.IO) {
                ProviderCatalog.create(spec, config).lookup(ip, settings.timeoutMs)
            }
            if (outcome.status == ProviderStatus.SUCCESS) {
                // 注意：cache 里必须把风险分/住宅属性一起存下，
                // 否则命中缓存后这些字段会变成 null —— 表现为“第一次查有分，刷新一下就没了”。
                cache.putProviderResult(key, com.zyvioip.app.model.ProviderResult(
                    providerId = providerId, status = com.zyvioip.app.model.ProviderStatus.SUCCESS,
                    signals = com.zyvioip.app.model.IpSignals(
                        ip = outcome.ip, country = outcome.country, countryCode = outcome.countryCode,
                        city = outcome.city, region = outcome.region,
                        company = outcome.company, asn = outcome.asn,
                        isResidential = outcome.isResidential,
                        isBroadcast = outcome.isBroadcast,
                        riskScores = outcome.riskScore?.let {
                            mapOf((ProviderCatalog.byId(providerId)?.name ?: providerId) to it)
                        }.orEmpty(),
                        rawFields = outcome.rawFields,
                        flags = outcome.flags,
                    ),
                    purity = outcome.purity, error = outcome.error,
                ))
            }
            outcome
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消必须原样抛出，否则会把“取消”当成失败
            throw e
        } catch (e: Exception) {
            // 不要吞掉具体原因：DNS 解析失败、证书错误、连接被重置……
            // 只有把真实异常显示出来，用户才能判断是网络问题还是服务问题。
            ProviderOutcome(
                providerId = providerId,
                status = ProviderStatus.ERROR,
                error = "${e.javaClass.simpleName}: ${e.message ?: "未知错误"}",
            )
        }
    }

    // 聚合计算（多次 map/filter + 构建 ProviderState）放到 IO，
    // 避免 6 次结果连写时把主线程占满。
    private suspend fun rebuild(ip: String, results: List<ProviderOutcome>) = withContext(Dispatchers.IO) {
        var signals = IpSignals(ip = ip)
        val risks = mutableListOf<Int>()
        for (result in results) {
            if (result.status != ProviderStatus.SUCCESS) continue
            val spec = ProviderCatalog.byId(result.providerId)
            // 记录每家的原始风险分，UI 上能直接展示“哪家给了多少分”
            val riskOfThis = result.riskScore ?: result.purity?.let { 100 - it }
            val riskMap =
                if (riskOfThis != null) mapOf((spec?.name ?: result.providerId) to riskOfThis) else emptyMap()

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
                    isResidential = result.isResidential,
                    isBroadcast = result.isBroadcast,
                    riskScores = riskMap,
                    flags = result.flags,
                ),
            )
            // 只有“明确给出评分”的源才参与平均，避免没数据的源把分数稀释
            if (spec?.scorable == true && riskOfThis != null) risks.add(riskOfThis)
        }
        val avgRisk = if (risks.isEmpty()) null else (risks.sum().toDouble() / risks.size).roundToInt().coerceIn(0, 100)

        // 汇总“IP 来源”判定：只采纳真的给了结论的源
        val verdicts = results
            .filter { it.status == ProviderStatus.SUCCESS && it.origin != null }
            .mapNotNull { r ->
                r.origin?.let { o ->
                    com.zyvioip.app.model.OriginVerdict(
                        origin = o,
                        source = ProviderCatalog.byId(r.providerId)?.name ?: r.providerId,
                    )
                }
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
            purity = avgRisk?.let { 100 - it },
            riskScore = avgRisk,
            scoredProviderCount = risks.size,
            type = classifyIpType(signals),
            origin = com.zyvioip.app.model.resolveOrigin(verdicts),
            originVerdicts = verdicts,
        )
    }

    private fun cacheKey(ip: String, id: String, key: String, email: String): String =
        listOf(ip, id, key, email).joinToString("|")

    private companion object {
        const val LOCAL_CACHE_KEY = "device"
        /** 同时进行的供应商请求上限，避免移动网络下并发过高集体超时。 */
        /**
         * 单次查询的整体时间预算（所有源加起来）。
         * 串行执行时必须有上限，否则源多时总耗时线性膨胀。
         */
        const val TOTAL_BUDGET_MS = 25_000L

    }
}
