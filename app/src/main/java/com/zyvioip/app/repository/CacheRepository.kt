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

    override suspend fun getLocalIp(key: String, ttlMs: Long): String? =
        memory.getLocalIp(key, ttlMs) ?: snapshot().local[key]?.takeIf {
            System.currentTimeMillis() - it.at <= ttlMs
        }?.ip

    override suspend fun putLocalIp(key: String, ip: String) {
        memory.putLocalIp(key, ip)
        store.edit { it[stringPreferencesKey(LOCAL_PREFIX + key)] = json.encodeToString(LocalEntry(ip, System.currentTimeMillis())) }
    }

    override suspend fun getProviderResult(key: String, ttlMs: Long): ProviderResult? =
        memory.getProviderResult(key, ttlMs) ?: snapshot().providers[key]?.takeIf { result ->
            System.currentTimeMillis() - result.fetchedAtMillis <= ttlMs
        }?.copy(fromCache = true)

    override suspend fun putProviderResult(key: String, result: ProviderResult) {
        memory.putProviderResult(key, result)
        store.edit { it[stringPreferencesKey(PROVIDER_PREFIX + key)] = json.encodeToString(result.copy(fromCache = false)) }
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
        },
        ip = signals.ip,
        country = signals.country,
        countryCode = signals.countryCode,
        city = signals.city,
        region = signals.region,
        company = signals.company,
        asn = signals.asn,
        purity = purity,
        flags = signals.flags,
        fromCache = fromCache,
    )
