package com.zyvioip.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
            // 这里曾有个坑：只要 activeRaw 不是 null 就当成“用户的有效选择”，
            // 但旧版本 removeProvider 会把空集合写成空串 ""，于是升级后读到 ""，
            // 解析成空集合 —— 表现就是「默认的那几个供应商不见了，要手动添加才出现」。
            // 现在区分三种情况：
            //   null      -> 全新安装，用默认集合
            //   ""        -> 历史版本写坏的空值，视为未初始化，回落默认集合
            //   "a,b,c"   -> 用户真实选择（即使只有 1 个也尊重）
            val activeRaw = prefs[ACTIVE]
            val parsedActive = activeRaw
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
            val effectiveActive = if (parsedActive.isEmpty() && (activeRaw == null || activeRaw.isBlank())) {
                ProviderCatalog.defaultActiveIds
            } else {
                // 配置迁移：剔除历史遗留、对免费用户必然失败的供应商，
                // 避免升级后首页永远挂着几张红卡（用户会以为程序坏了）。
                //   ipdatafree  -> 无 key 一律 401
                //   getipintel  -> 强制要邮箱，且按出口 IP 封禁（-5）
                migrateActiveIds(parsedActive, prefs[CONFIG_VERSION] ?: 0)
            }
            LookupSettings(
                activeIds = effectiveActive,
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
        prefs[CONFIG_VERSION] = CURRENT_CONFIG_VERSION
        prefs[AUTO_LOOKUP] = settings.autoLookup
        prefs[SHOW_FLAGS] = settings.showFlags
        prefs[NOTIFICATIONS] = settings.notifications
    }

    /**
     * 按配置版本做一次性迁移，迁移后把版本号写回，避免反复调整用户的选择。
     */
    private fun migrateActiveIds(active: Set<String>, storedVersion: Int): Set<String> {
        if (storedVersion >= CURRENT_CONFIG_VERSION) return active
        val deprecated = setOf("ipdatafree", "getipintel")
        return active - deprecated
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
        private val CONFIG_VERSION = intPreferencesKey("config_version")

        /**
         * 配置迁移版本号。改动默认供应商集合时 +1，老用户升级会自动剔除失效项。
         * 1: 移除 ipdatafree(401) / getipintel(需邮箱且按出口 IP 封禁)
         */
        private const val CURRENT_CONFIG_VERSION = 1
    }
}
