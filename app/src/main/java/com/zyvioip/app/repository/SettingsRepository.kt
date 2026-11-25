package com.zyvioip.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "zyvioip_settings")

data class LookupSettings(
    val activeIds: Set<String> = ProviderCatalog.defaultActiveIds,
    val keys: Map<String, String> = emptyMap(),
    val emails: Map<String, String> = emptyMap(),
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    val useEnabledAsDenominator: Boolean = false,
    val autoLookup: Boolean = true,
    val showFlags: Boolean = true,
    val notifications: Boolean = false,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val DEFAULT_CACHE_TTL_MS = 12 * 60 * 60 * 1000L
    }
}

/** User settings persisted in DataStore; credentials are stored locally and never logged. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    suspend fun load(): LookupSettings = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            // 用“键是否存在”判断首次启动，而不是“值是否为空”。
            // 旧逻辑 activeIds 为空就回退默认值，导致用户把供应商全删光后重启又被全部加回来。
            val activeRaw = prefs[ACTIVE]
            LookupSettings(
                activeIds = if (activeRaw == null) ProviderCatalog.defaultActiveIds
                else activeRaw.split(',').filter { it.isNotBlank() }.toSet(),
                keys = decodeMap(prefs[KEYS]),
                emails = decodeMap(prefs[EMAILS]),
                timeoutMs = prefs[TIMEOUT] ?: LookupSettings.DEFAULT_TIMEOUT_MS,
                cacheTtlMs = prefs[TTL] ?: LookupSettings.DEFAULT_CACHE_TTL_MS,
                useEnabledAsDenominator = prefs[DENOMINATOR] ?: false,
                autoLookup = prefs[AUTO_LOOKUP] ?: true,
                showFlags = prefs[SHOW_FLAGS] ?: true,
                notifications = prefs[NOTIFICATIONS] ?: false,
            )
        }.first()

    suspend fun save(settings: LookupSettings) = store.edit { prefs ->
        prefs[ACTIVE] = settings.activeIds.joinToString(",")
        prefs[KEYS] = encodeMap(settings.keys)
        prefs[EMAILS] = encodeMap(settings.emails)
        prefs[TIMEOUT] = settings.timeoutMs
        prefs[TTL] = settings.cacheTtlMs
        prefs[DENOMINATOR] = settings.useEnabledAsDenominator
        prefs[AUTO_LOOKUP] = settings.autoLookup
        prefs[SHOW_FLAGS] = settings.showFlags
        prefs[NOTIFICATIONS] = settings.notifications
    }

    private fun encodeMap(map: Map<String, String>): String = buildString {
        map.forEach { (k, v) ->
            if (k.isNotBlank()) append(k.length).append(':').append(k).append(v.length).append(':').append(v)
        }
    }

    private fun decodeMap(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < raw.length) {
            val c1 = raw.indexOf(':', i).takeIf { it >= 0 } ?: break
            val keyLength = raw.substring(i, c1).toIntOrNull() ?: break
            val keyStart = c1 + 1
            val valueLengthStart = keyStart + keyLength
            if (valueLengthStart >= raw.length) break
            val c2 = raw.indexOf(':', valueLengthStart).takeIf { it >= 0 } ?: break
            val valueLength = raw.substring(valueLengthStart, c2).toIntOrNull() ?: break
            val valueStart = c2 + 1
            if (valueStart + valueLength > raw.length) break
            result[raw.substring(keyStart, valueLengthStart)] = raw.substring(valueStart, valueStart + valueLength)
            i = valueStart + valueLength
        }
        return result
    }

    companion object {
        private val ACTIVE = stringPreferencesKey("active_ids")
        private val KEYS = stringPreferencesKey("keys")
        private val EMAILS = stringPreferencesKey("emails")
        private val TIMEOUT = longPreferencesKey("timeout_ms")
        private val TTL = longPreferencesKey("cache_ttl_ms")
        private val DENOMINATOR = booleanPreferencesKey("enabled_denominator")
        private val AUTO_LOOKUP = booleanPreferencesKey("auto_lookup")
        private val SHOW_FLAGS = booleanPreferencesKey("show_flags")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications")
    }
}
