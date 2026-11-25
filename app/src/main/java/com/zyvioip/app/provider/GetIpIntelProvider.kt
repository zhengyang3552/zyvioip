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
        val map = parseObject(json, body)
        if (textField(map, "error") != null && textField(map, "ip") == null) {
            return result(ProviderStatus.ERROR, error = textField(map, "error"))
        }
        val probability = numberField(map, "result")
            ?: return result(ProviderStatus.ERROR, error = "服务未返回评分")
        val asDouble = probability.toDouble()
        // GetIPIntel 用负数返回错误码（-1 无效 IP、-2 未提供联系人、-3 配额、-4 访问受限…），
        // 旧代码把 -1 当成合法评分算出 purity=100（最干净），正好把最脏的情况显示成最干净。
        if (asDouble < 0) {
            val reason = when (asDouble.toInt()) {
                -1 -> "IP 地址无效"
                -2 -> "需要有效的联系邮箱"
                -3, -4 -> "查询配额已用尽或被限制"
                else -> "服务返回错误码 ${asDouble.toInt()}"
            }
            return result(ProviderStatus.ERROR, error = reason)
        }
        val normalized = if (asDouble <= 1.0) asDouble * 100.0 else asDouble
        val flags = if (normalized >= 50.0) setOf(ProviderFlag.PROXY) else emptySet()
        return result(
            status = ProviderStatus.SUCCESS,
            ip = queriedIp,
            purity = toPurity(normalized),
            flags = flags,
            message = map["message"] as? String,
        )
    }
}
