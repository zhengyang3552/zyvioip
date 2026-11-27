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
        // GetIPIntel 的 result 实测只返回 0 或 1（不是连续概率曲线）：
        //   0 = 未判定为代理/VPN
        //   1 = 判定为代理/VPN
        // 因此它只能用来看「是不是代理」，不能当作 0~100 的风险程度。
        //
        // 之前按「概率 × 100」换算成风险分，会得出 0 或 100 两个极端值，
        // 混进平均分后会把整体风险拉到失真（一个源就能把 73 拉成 93）。
        //
        // 现在：不产出 riskScore，只给 PROXY 标记，并明确标注它不参与评分。
        val isProxy = when {
            asDouble >= 1.0 -> true
            else -> false
        }
        val flags = if (isProxy) setOf(ProviderFlag.PROXY) else emptySet()

        return result(
            status = ProviderStatus.SUCCESS,
            rawFields = flattenForDisplay(map),
            ip = queriedIp,
            purity = null,
            riskScore = null,
            flags = flags,
            message = if (isProxy) {
                "判定为代理/VPN（GetIPIntel 只给二元结论，不参与风险评分）"
            } else {
                "未判定为代理/VPN（GetIPIntel 只给二元结论，不参与风险评分）"
            },
        )
    }
}
