package com.zyvioip.app.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Detects the device's public IP using independent fallbacks with a shared timeout. */
class LocalIpRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        // 有些探测端点会 30x 跳转（例如 ipapi.is/json/ 返回 308），
        // OkHttp 默认不跟随跨协议重定向，这里打开，避免“端点能通但被判定失败”。
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {
    companion object {
        // 实测（2026-11）：
        //   api.ipdata.co 无 key 固定返回 401 -> 永远失败，已移除
        //   ipapi.is/json/ 返回 308 重定向，而 OkHttp 默认不跟随跨协议重定向 -> 永远失败，已移除
        // 换成真正可用的备用源。
        val DEFAULT_ENDPOINTS = listOf(
            "https://api.ipify.org?format=json",
            "https://api64.ipify.org?format=json",
            "https://ipwho.is/",          // 返回 {"ip":"..."}，免密钥
            "https://api.ip.sb/geoip",    // 返回 {"ip":"..."}
        )
        const val LOCAL_CACHE_TTL_MS = 8 * 60 * 1000L
    }

    suspend fun detect(): String? = withContext(Dispatchers.IO) {
        // 先串行试前两个（最快最稳的 ipify），拿到就返回。
        // 只有都失败时才并发探测剩余端点 —— 这样冷启动通常只发 1 个请求，
        // 不会和后面的 5 个供应商请求抢网络。
        val primary = endpoints.take(2)
        val fallback = endpoints.drop(2)

        for (url in primary) {
            val ip = runCatching { fetchIp(url) }.getOrNull()
            if (ip != null && com.zyvioip.app.util.isValidIp(ip)) return@withContext ip
        }
        if (fallback.isEmpty()) return@withContext null

        coroutineScope {
            val jobs = fallback.map { url ->
                async { runCatching { fetchIp(url) }.getOrNull() }
            }
            val results = withTimeoutOrNull(5_000L) { jobs.map { it.await() } } ?: emptyList()
            results.firstOrNull { it != null && com.zyvioip.app.util.isValidIp(it) }
        }
    }

    private fun fetchIp(url: String): String? {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string().orEmpty()
            if (body.length > 4096) return@use null
            extractIp(body)
        }
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
