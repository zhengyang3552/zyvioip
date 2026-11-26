package com.zyvioip.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import androidx.datastore.preferences.preferencesDataStore
import com.zyvioip.app.model.ProviderResult
import com.zyvioip.app.model.ProviderStatus
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

    override suspend fun getLocalIp(key: String, ttlMs: Long): String? =
        memory.getLocalIp(key, ttlMs) ?: snapshot().local[key]?.takeIf {
            System.currentTimeMillis() - it.at <= ttlMs
        }?.ip

    override suspend fun putLocalIp(key: String, ip: String) {
        memory.putLocalIp(key, ip)
        store.edit { it[stringPreferencesKey(LOCAL_PREFIX + key)] = json.encodeToString(LocalEntry(ip, System.currentTimeMillis())) }
    }

    override suspend fun getProviderResult(key: String, ttlMs: Long): ProviderResult? =
        // 只查内存。
        //
        // 查询结果不再持久化到 DataStore：
        // 每条含 rawFields（ipto.to 单条约 1.3KB），而缓存 key 里带 apiKey/email，
        // 改过一次配置后旧条目就变成永远不会命中的孤儿记录。
        // 这些条目在重启时被全量读出并逐个 JSON 解码，
        // 正是「第一次进去还行、退出重进后越来越卡」的根因。
        //
        // 内存缓存足够：重启后重新查一遍约几百毫秒，用户拿到的是最新数据；
        // 而持久化带来的解码与 I/O 开销远大于它的收益。
        memory.getProviderResult(key, ttlMs)

    /**
     * 启动时把持久化的「本机 IP」读进内存。
     *
     * 只处理 1 条记录 —— 查询结果已改为纯内存缓存，不再需要全量解码。
     * 之前这里会把 DataStore 里所有 provider 条目读出来逐条 JSON 解析，
     * 历史记录越多启动越慢，正是「重启后变卡」的直接原因。
     */
    suspend fun preload() {
        if (preloaded) return
        runCatching {
            val snap = snapshot()
            snap.local.forEach { (key, value) -> memory.putLocalIp(key, value.ip) }
        }
        preloaded = true
    }

    override suspend fun putProviderResult(key: String, result: ProviderResult) {
        memory.putProviderResult(key, result)
    }

    override suspend fun clear() {
        memory.clear()
        store.edit { it.clear() }
    }

    /**
     * 只读出「本机公网 IP」这一条持久缓存。
     *
     * 查询结果（provider 级）**不再持久化** —— 原因见 getProviderResult / putProviderResult。
     * 这样启动时最多解码 1 条，彻底消除「重启后越来越卡」的来源。
     */
    private suspend fun snapshot(): Snapshot = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val local = prefs.asMap().entries
                .filter { it.key.name.startsWith(LOCAL_PREFIX) }
                .mapNotNull { entry ->
                    runCatching { json.decodeFromString<LocalEntry>(entry.value as String) }
                        .getOrNull()
                        ?.let { entry.key.name.removePrefix(LOCAL_PREFIX) to it }
                }
                .toMap()
            Snapshot(local = local)
        }.first()

    /**
     * 从 ProviderResult 的 JSON 里快速取出 fetchedAtMillis，用于排序。
     * 只做字符串扫描，不解析整棵树 —— 目的是在解码前先筛掉旧记录。
     */
    private fun quickTimestamp(rawJson: String): Long {
        val idx = rawJson.lastIndexOf("\"fetchedAtMillis\"")
        if (idx < 0) return 0L
        var i = idx + "\"fetchedAtMillis\"".length
        while (i < rawJson.length && (rawJson[i] == ':' || rawJson[i] == ' ')) i++
        var end = i
        while (end < rawJson.length && rawJson[end].isDigit()) end++
        return rawJson.substring(i, end).toLongOrNull() ?: 0L
    }

    private data class Snapshot(
        val local: Map<String, LocalEntry> = emptyMap(),
    )

    @Serializable private data class LocalEntry(val ip: String, val at: Long)

    companion object {
        private const val LOCAL_PREFIX = "local:"
        private const val PROVIDER_PREFIX = "provider:"

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
