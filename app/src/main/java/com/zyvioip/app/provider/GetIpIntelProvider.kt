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
            // 服务会把具体原因放在 message 里，优先展示原文，代码只做兜底。
            val fallback = when (asDouble.toInt()) {
                -1 -> "IP 地址无效"
                -2 -> "请求参数无效"
                -3 -> "不支持该查询格式"
                -4 -> "查询配额已用尽"
                -5 -> "访问受限（请稍后重试）"
                -6 -> "需要填写有效的联系邮箱"
                else -> "服务返回错误码 ${asDouble.toInt()}"
            }
            return result(ProviderStatus.ERROR, error = textField(map, "message") ?: fallback)
        }
        // GetIPIntel 的 result 是「该 IP 是代理/VPN 的概率」，范围 0~1（若 >1 则按百分制）。
        // 关键点：result = 0 表示「没有证据表明是代理」，不是「数据缺失」，也不是风险 0。
        // 旧实现把它算成 purity=100，用户看到的是“纯净度 100”，很容易误会成评分很高；
        // 现在统一改成风险分语义：risk = 概率 * 100，同时把话说明白。
        val probability = if (asDouble <= 1.0) asDouble else asDouble / 100.0
        val risk = (probability * 100.0).toInt().coerceIn(0, 100)
        val flags = if (risk >= 50) setOf(ProviderFlag.PROXY) else emptySet()
        val verdict = when {
            risk >= 75 -> "高度疑似代理/VPN"
            risk >= 50 -> "疑似代理/VPN"
            risk >= 20 -> "可能有代理特征"
            else -> "未发现代理/VPN 特征"
        }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = queriedIp,
            riskScore = risk,
            purity = toPurity(risk),
            flags = flags,
            message = "$verdict · 代理概率 ${"%.1f".format(probability * 100)}%",
        )
    }
}
