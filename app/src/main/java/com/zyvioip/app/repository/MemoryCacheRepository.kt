package com.zyvioip.app.repository

import com.zyvioip.app.model.ProviderResult
import com.zyvioip.app.model.ProviderStatus
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
        provider[key] = System.currentTimeMillis() to result.copy(fromCache = false)
    }

    /** Retrieve regardless of TTL (used for retry or a re-added provider). */
    suspend fun peekProviderResult(key: String): ProviderResult? = mutex.withLock {
        provider[key]?.second?.copy(fromCache = true)
    }

    override suspend fun clear() = mutex.withLock {
        local.clear(); provider.clear()
    }

    private data class LocalCacheEntry(val ip: String, val at: Long)
}
