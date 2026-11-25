package com.zyvioip.app.provider

import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.max
import java.util.concurrent.TimeUnit.NANOSECONDS

/**
 * Base class for every upstream provider. Each lookup is a cancellable OkHttp call with a hard
 * per-request timeout. Parsing is isolated so malformed JSON is reported as a clean [ProviderStatus.ERROR].
 */
abstract class IpProvider(
    val spec: ProviderSpec,
    config: ProviderConfig? = null,
    client: OkHttpClient? = null,
) {
    protected val config: ProviderConfig = config ?: ProviderConfig(spec.id)
    protected val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(DEFAULT_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS)
        .build()

    val id: String get() = spec.id

    open fun needsConfiguration(): Boolean = when {
        spec.requiresKey && !config.hasKey -> true
        spec.requiresEmail && !config.hasEmail -> true
        else -> false
    }

    suspend fun lookup(ip: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ProviderOutcome {
        if (needsConfiguration()) {
            return result(ProviderStatus.NEEDS_CONFIG, error = "尚未配置密钥或邮箱")
        }
        val started = System.nanoTime()
        return try {
            val response = execute(buildRequest(ip), timeoutMs)
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                val code = response.code
                result(
                    ProviderStatus.ERROR,
                    error = if (code in 400..499) "请求被拒绝 ($code)" else "服务暂时不可用 ($code)",
                )
            } else {
                try {
                    parse(body, ip).withLatency(System.nanoTime())
                } catch (_: Exception) {
                    result(ProviderStatus.ERROR, error = "响应解析失败，请稍后重试")
                }
            }
        } catch (_: IOException) {
            result(ProviderStatus.ERROR, error = "网络超时或不可达，请稍后重试")
        } catch (_: IllegalArgumentException) {
            result(ProviderStatus.ERROR, error = "请求参数无效")
        } catch (_: Exception) {
            result(ProviderStatus.ERROR, error = "查询失败，请稍后重试")
        }
    }

    protected abstract fun buildRequest(ip: String): Request

    protected abstract fun parse(body: String, queriedIp: String): ProviderOutcome

    /** 仅供 JVM 单测验证解析逻辑，不发起网络请求。 */
    internal fun parseForTest(body: String, ip: String): ProviderOutcome = parse(body, ip)

    protected fun result(
        status: ProviderStatus,
        ip: String? = null,
        country: String? = null,
        countryCode: String? = null,
        city: String? = null,
        region: String? = null,
        company: String? = null,
        asn: String? = null,
        purity: Int? = null,
        riskScore: Int? = null,
        isResidential: Boolean? = null,
        isBroadcast: Boolean? = null,
        flags: Set<String> = emptySet(),
        message: String? = null,
        error: String? = null,
    ): ProviderOutcome = ProviderOutcome(
        providerId = spec.id,
        status = status,
        ip = ip,
        country = country,
        countryCode = countryCode,
        city = city,
        region = region,
        company = company,
        asn = asn,
        purity = purity?.coerceIn(0, 100),
        riskScore = riskScore?.coerceIn(0, 100),
        isResidential = isResidential,
        isBroadcast = isBroadcast,
        flags = flags,
        message = message,
        error = error,
    )

    protected fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it != "-" }

    protected fun boolFlag(value: Boolean?, flag: String): Set<String> =
        if (value == true) setOf(flag) else emptySet()

    protected fun suspiciousFlag(value: String?): Set<String> = when (value?.lowercase()) {
        "yes", "true", "1", "proxy", "vpn", "tor", "datacenter", "hosting", "abuser", "mobile" -> setOf(value.lowercase())
        else -> emptySet()
    }

    /** Higher score means more suspicious for every provider that exposes one. */
    protected fun toPurity(value: Number?): Int? {
        if (value == null) return null
        val n = value.toDouble()
        if (!n.isFinite()) return null
        return (100 - n.coerceIn(0.0, 100.0)).toInt()
    }

    private fun elapsed(started: Long): Long = NANOSECONDS.toMillis(System.nanoTime() - started)

    private fun ProviderOutcome.withLatency(ms: Long): ProviderOutcome = copy(latencyMs = max(0, ms))

    private suspend fun execute(request: Request, timeoutMs: Long): Response {
        val timeout = max(1_000L, timeoutMs)
        // 注意：call.timeout() 必须在 enqueue() 之前设置才可靠。
        // 原来写在 enqueue 之后（且没有 try/finally 复位），对已排队的 Call 改超时是不生效的，
        // 结果就是“设置里改超时没用”，请求可能一直挂到 OkHttp 的 callTimeout。
        val client = http.newBuilder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .callTimeout(timeout + 1_000, TimeUnit.MILLISECONDS)
            .build()
        val call = client.newCall(request)
        return try {
            suspendCancellableCoroutine { cont ->
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) cont.resume(response)
                        else response.close()
                    }
                })
                cont.invokeOnCancellation { runCatching { call.cancel() } }
            }
        } finally {
            Unit
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 8_000
    }
}

/** Reads the first present numeric field from a JSON object without throwing on other shapes. */
fun Json.firstInt(body: String, vararg names: String): Int? {
    val obj = runCatching { parseToMap(body) }.getOrNull() ?: return null
    for (name in names) {
        val v = obj[name]
        if (v is Number) return v.toInt()
        if (v is String) v.toIntOrNull()?.let { return it }
    }
    return null
}

private fun Json.parseToMap(body: String): Map<String, Any?> =
    decodeFromString<Map<String,Any?>>(body)
