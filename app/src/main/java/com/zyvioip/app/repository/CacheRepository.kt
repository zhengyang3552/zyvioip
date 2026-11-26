package com.zyvioip.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import androidx.datastore.preferences.preferencesDataStore
import com.zyvioip.app.model.ProviderResult
import com.zyvioip.app.model.ProviderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.coreCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "zyvioip_core_cache")

interface CacheRepository {
    suspend fun getLocalIp(key: String, ttlMs: Long): String?
    suspend fun putLocalIp(key: String, ip: String)
    suspend fun getProviderResult(key: String, ttlMs: Long): ProviderResult?
    suspend fun putProviderResult(key: String, result: ProviderResult)
    suspend fun clear()
}

/** DataStore-backed persistent cache with a fast in-memory mirror. */
class DataStoreCacheRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : CacheRepository {
    private val store = context.applicationContext.coreCacheDataStore
    private val memory = MemoryCacheRepository()

    @Volatile private var preloaded = false

    /** 后台落盘用，避免写 DataStore 阻塞查询主流程。 */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getLocalIp(key: String, ttlMs: Long): String? =
        memory.getLocalIp(key, ttlMs) ?: snapshot().local[key]?.takeIf {
            System.currentTimeMillis() - it.at <= ttlMs
        }?.ip

    override suspend fun putLocalIp(key: String, ip: String) {
        memory.putLocalIp(key, ip)
        store.edit { it[stringPreferencesKey(LOCAL_PREFIX + key)] = json.encodeToString(LocalEntry(ip, System.currentTimeMillis())) }
    }

    override suspend fun getProviderResult(key: String, ttlMs: Long): ProviderResult? {
        // 只查内存。原来这里的 `?: snapshot()` 是性能灾难：
        // snapshot() 会把整个 DataStore 的**所有条目**读出来并逐个 JSON 解码，
        // 而每个供应商首次查询都必然内存未命中（冷启动），于是 6 个源各触发一次全量读，
        // 在移动设备上会明显拖慢甚至拖垮首批请求 —— 表现就是“第一次全失败、第二次成功”。
        // 持久层改为启动时预热一次（见 preload()），查询路径不再碰磁盘。
        memory.getProviderResult(key, ttlMs)?.let { return it }
        if (!preloaded) return null
        return snapshot().providers[key]?.takeIf { result ->
            System.currentTimeMillis() - result.fetchedAtMillis <= ttlMs
        }?.copy(fromCache = true)
    }

    /**
     * 启动时把持久缓存一次性灌进内存，之后查询只走内存。
     * 重复调用是幂等的。
     */
    suspend fun preload() {
        if (preloaded) return
        runCatching {
            val snap = snapshot()
            snap.providers.forEach { (key, value) -> memory.putProviderResult(key, value) }
            snap.local.forEach { (key, value) -> memory.putLocalIp(key, value.ip) }

            // 历史版本没有容量上限，可能已经堆了大量条目。
            // 启动时清理一次，避免后续每次 preload 都全量解码。
            if (snap.providers.size > MAX_CACHE_ENTRIES) {
                store.edit { prefs -> evictIfNeeded(prefs, keepKeyName = "") }
            }
        }
        preloaded = true
    }

    override suspend fun putProviderResult(key: String, result: ProviderResult) {
        memory.putProviderResult(key, result)
        // 落盘放到后台作用域，不阻塞本次查询返回。
        // 6 个源几乎同时完成时，串行 store.edit 会互相排队，
        // 让“最后几个源”看起来像卡住/失败。
        persistScope.launch {
            runCatching {
                store.edit { prefs ->
                    val entryKey = stringPreferencesKey(PROVIDER_PREFIX + key)
                    prefs[entryKey] = json.encodeToString(result.copy(fromCache = false))
                    // 容量控制：缓存条目此前只增不减，查过的 IP 越多
                    // DataStore 文件越大，preload 时全部解码也就越慢 ——
                    // 这是“进页面就卡”的重要来源。
                    evictIfNeeded(prefs, keepKeyName = entryKey.name)
                }
            }
        }
    }

    /**
     * 超出上限时，按 fetchedAtMillis 淘汰最旧的条目。
     * 单次 edit 内完成，避免额外遍历 DataStore。
     */
    private fun evictIfNeeded(prefs: MutablePreferences, keepKeyName: String) {
        val providerEntries = prefs.asMap().keys
            .filter { it.name.startsWith(PROVIDER_PREFIX) }
        if (providerEntries.size <= MAX_CACHE_ENTRIES) return

        // 解不出内容的条目也单列出来：它们占空间却没价值，优先清掉
        val decoded = mutableListOf<Pair<Preferences.Key<String>, Long>>()
        val broken = mutableListOf<Preferences.Key<*>>()
        providerEntries.forEach { k ->
            @Suppress("UNCHECKED_CAST")
            val typed = k as Preferences.Key<String>
            val raw = prefs[typed]
            if (raw == null) {
                broken += k
            } else {
                runCatching { json.decodeFromString<ProviderResult>(raw) }
                    .onSuccess { decoded += typed to it.fetchedAtMillis }
                    .onFailure { broken += k }
            }
        }

        broken.forEach { prefs.remove(it) }

        // 按“最久未查”排序，保留最近的 MAX_CACHE_ENTRIES 条
        val excess = decoded.size - MAX_CACHE_ENTRIES
        if (excess <= 0) return
        decoded
            .filter { it.first.name != keepKeyName }   // 不淘汰刚写入的那条
            .sortedBy { it.second }
            .take(excess)
            .forEach { (k, _) -> prefs.remove(k) }
    }

    override suspend fun clear() {
        memory.clear()
        store.edit { it.clear() }
    }

    private suspend fun snapshot(): Snapshot = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            Snapshot(
                local = prefs.asMap().entries.filter { it.key.name.startsWith(LOCAL_PREFIX) }
                    .mapNotNull { entry -> runCatching { json.decodeFromString<LocalEntry>(entry.value as String) }.getOrNull()?.let { entry.key.name.removePrefix(LOCAL_PREFIX) to it } }
                    .toMap(),
                providers = prefs.asMap().entries.filter { it.key.name.startsWith(PROVIDER_PREFIX) }
                    .mapNotNull { entry -> runCatching { json.decodeFromString<ProviderResult>(entry.value as String) }.getOrNull()?.let { entry.key.name.removePrefix(PROVIDER_PREFIX) to it } }
                    .toMap(),
            )
        }.first()

    private data class Snapshot(
        val local: Map<String, LocalEntry> = emptyMap(),
        val providers: Map<String, ProviderResult> = emptyMap(),
    )

    @Serializable private data class LocalEntry(val ip: String, val at: Long)

    companion object {
        private const val LOCAL_PREFIX = "local:"
        private const val PROVIDER_PREFIX = "provider:"

        /**
         * 持久缓存的条目上限。
         * 每条含 rawFields（ipto.to 单条约 1.3KB），60 条约 500KB，
         * 既能覆盖常用 IP，又不至于让 preload 变慢。
         */
        private const val MAX_CACHE_ENTRIES = 60
    }
}

/** Compatibility shim for domain snapshots used by older tests. */
@Deprecated("ProviderOutcome is the canonical provider result in the app layer")
fun ProviderResult.asOutcome(): com.zyvioip.app.provider.ProviderOutcome =
    com.zyvioip.app.provider.ProviderOutcome(
        providerId = providerId,
        status = when (status) {
            ProviderStatus.SUCCESS -> com.zyvioip.app.provider.ProviderStatus.SUCCESS
            ProviderStatus.ERROR -> com.zyvioip.app.provider.ProviderStatus.ERROR
            ProviderStatus.NEEDS_CONFIG -> com.zyvioip.app.provider.ProviderStatus.NEEDS_CONFIG
            ProviderStatus.LOADING -> com.zyvioip.app.provider.ProviderStatus.LOADING
            ProviderStatus.PENDING -> com.zyvioip.app.provider.ProviderStatus.PENDING
            ProviderStatus.SKIPPED -> com.zyvioip.app.provider.ProviderStatus.SKIPPED
        },
        ip = signals.ip,
        country = signals.country,
        countryCode = signals.countryCode,
        city = signals.city,
        region = signals.region,
        company = signals.company,
        asn = signals.asn,
        purity = purity,
        // 风险分/住宅属性从缓存还原，否则命中缓存后卡片会丢分
        riskScore = signals.riskScores.values.firstOrNull(),
        isResidential = signals.isResidential,
        isBroadcast = signals.isBroadcast,
        rawFields = signals.rawFields,
        flags = signals.flags,
        fromCache = fromCache,
    )
