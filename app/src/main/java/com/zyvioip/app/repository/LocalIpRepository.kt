package com.zyvioip.app.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Detects the device's public IP using independent fallbacks with a shared timeout. */
class LocalIpRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS).build(),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {
    companion object {
        val DEFAULT_ENDPOINTS = listOf(
            "https://api.ipify.org?format=json",
            "https://api64.ipify.org?format=json",
            "https://api.ipdata.co?fields=ip",
            "https://ipapi.is/json/",
        )
        const val LOCAL_CACHE_TTL_MS = 8 * 60 * 1000L
    }

    suspend fun detect(): String? = withContext(Dispatchers.IO) {
        for (url in endpoints) {
            val ip = try {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.length > 4096) return@use null
                    extractIp(body)
                }
            } catch (_: Exception) {
                null
            }
            if (ip != null && com.zyvioip.app.util.isValidIp(ip)) return@withContext ip
        }
        null
    }

    /** Extremely small JSON/text parser; avoids throwing on unexpected shapes. */
    internal fun extractIp(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.length <= 64 && com.zyvioip.app.util.isValidIp(trimmed)) return trimmed
        for (key in listOf("ip", "ipAddress", "ip_address", "query")) {
            val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]{1,64})\"")
            val value = pattern.find(trimmed)?.groupValues?.get(1) ?: continue
            if (com.zyvioip.app.util.isValidIp(value)) return value
        }
        return null
    }
}
