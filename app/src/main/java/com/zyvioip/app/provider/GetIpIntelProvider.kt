package com.zyvioip.app.provider

import okhttp3.Request

/** GetIPIntel: probability (0..1) that an address is a proxy/VPN. */
class GetIpIntelProvider(
    spec: ProviderSpec = ProviderCatalog.byId("getipintel")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("check.getipintel.net") {
            addPathSegment("check.php")
            addQueryParameter("ip", ip)
            addQueryParameter("contact", config.email.trim())
            addQueryParameter("format", "json")
            addQueryParameter("flags", "m")
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = json.decodeFromString<Map<String, Any?>>(body)
        val probability = numberField(map, "result")
            ?: return result(ProviderStatus.ERROR, error = "服务未返回评分")
        val asDouble = probability.toDouble()
        val normalized = if (asDouble <= 1.0) asDouble * 100.0 else asDouble
        val flags = if (normalized >= 50.0) setOf(ProviderFlag.PROXY) else emptySet()
        return result(
            status = if (asDouble >= 0.0) ProviderStatus.SUCCESS else ProviderStatus.ERROR,
            ip = queriedIp,
            purity = toPurity(normalized),
            flags = flags,
            message = map["message"] as? String,
        )
    }
}
