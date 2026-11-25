package com.zyvioip.app.provider

import okhttp3.Request
import kotlin.math.roundToInt

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
        // status 字段必须检查：服务在出错时会明确给 "error"，
        // 此时 result 可能是 -1~-6（错误码）。之前完全没看 status，
        // 只靠 result < 0 兜底，边界情况会误判。
        val serviceStatus = textField(map, "status")?.lowercase()
        val probability = numberField(map, "result")
            ?: return result(ProviderStatus.ERROR, error = "服务未返回评分")
        if (serviceStatus == "error" && probability.toDouble() >= 0) {
            // status=error 但返回了非负值：属于异常组合，按失败处理，不要编造分数
            return result(
                ProviderStatus.ERROR,
                error = textField(map, "message") ?: "服务返回错误状态",
            )
        }
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
        // 注意：上面已经有一个 probability(Number) 变量，这里换名避免重复声明
        // GetIPIntel 的 result 官方定义是 0.0000~1.0000 的概率。
        // 这里必须严格按文档解释：
        //   * 只有 [0, 1] 区间才视为概率
        //   * 大于 1 的（例如 2~100）说明服务返回的是百分制或异常值，
        //     直接按百分制处理，绝不能再用 "<=1.0" 这种宽松判断
        //     —— 之前 1.0 会同时匹配“概率 1.0”和“百分制 1”，语义含糊。
        val proxyProbability = when {
            asDouble in 0.0..1.0 -> asDouble
            asDouble > 1.0 -> (asDouble / 100.0).coerceIn(0.0, 1.0)
            else -> 0.0
        }
        val risk = (proxyProbability * 100.0).roundToInt().coerceIn(0, 100)
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
            message = "$verdict · 代理概率 ${"%.1f".format(proxyProbability * 100)}%",
        )
    }
}
