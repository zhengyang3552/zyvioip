package com.zyvioip.app.repository

import com.zyvioip.app.model.ProviderResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Pure in-memory TTL cache, safe to unit-test on the JVM. */
class MemoryCacheRepository : CacheRepository {
    private val mutex = Mutex()
    private val local = mutableMapOf<String, LocalCacheEntry>()
    private val provider = mutableMapOf<String, Pair<Long, ProviderResult>>()

    override suspend fun getLocalIp(key: String, ttlMs: Long): String? = mutex.withLock {
        val entry = local[key] ?: return@withLock null
        if (System.currentTimeMillis() - entry.at <= ttlMs) entry.ip else null
    }

    override suspend fun putLocalIp(key: String, ip: String) = mutex.withLock {
        local[key] = LocalCacheEntry(ip, System.currentTimeMillis())
    }

    override suspend fun getProviderResult(key: String, ttlMs: Long): ProviderResult? = mutex.withLock {
        val entry = provider[key] ?: return@withLock null
        if (System.currentTimeMillis() - entry.first > ttlMs) null else entry.second.copy(fromCache = true)
    }

    override suspend fun putProviderResult(key: String, result: ProviderResult) = mutex.withLock {
        val storedAt = System.currentTimeMillis()
        provider[key] = storedAt to result.copy(fromCache = false)
        // 内存里也要有上限。preload() 会把磁盘里的条目全量灌进来，
        // 而磁盘上限只约束新写入；历史遗留或 key 变体（改过 API Key）会让
        // 内存无限增长 —— 这些不会被再次命中，却一直占着内存，
        // 表现为「重启后越来越卡」。
        if (provider.size > MAX_ENTRIES) {
            provider.entries
                .sortedBy { it.value.first }
                .take(provider.size - MAX_ENTRIES)
                .map { it.key }
                .forEach { provider.remove(it) }
        }
    }

    override suspend fun clear() = mutex.withLock {
        local.clear(); provider.clear()
    }

    private data class LocalCacheEntry(val ip: String, val at: Long)

    private companion object {
        const val MAX_ENTRIES = 60
    }
}
