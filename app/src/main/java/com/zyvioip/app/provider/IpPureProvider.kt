package com.zyvioip.app.provider

import okhttp3.Request

/**
 * IPPure：公开免密钥接口 `https://my.ippure.com/v1/info`。
 *
 * **重要限制（2026-11 实测）**：该接口只返回“调用方自己的出口 IP”，
 * 传 `?ip=` / `?q=` / 路径式都不会生效，全部返回调用方地址。
 * 因此本 provider 只能用于查询本机公网 IP；查询其它 IP 时会明确报错，
 * 而不是把别人的数据当成目标 IP 显示（那会非常误导）。
 *
 * 响应示例：
 * ```json
 * {"ip":"...","asn":13335,"asOrganization":"Cloudflare, Inc.",
 *  "country":"United States","countryCode":"US","region":"California","regionCode":"CA",
 *  "city":"Los Angeles","timezone":"America/Los_Angeles",
 *  "fraudScore":75,"isResidential":false,"isBroadcast":false}
 * ```
 * `fraudScore` 越大越危险；`isResidential` 为 true 表示住宅/原生 IP。
 */
class IpPureProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ippure")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(url("my.ippure.com") { addPathSegments("v1/info") })

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        if (map.isEmpty()) {
            return result(ProviderStatus.ERROR, error = "响应解析失败，请稍后重试")
        }
        val returnedIp = textField(map, "ip")
        // 接口只会返回调用方 IP，无法查询任意地址。若返回的 IP 与目标不一致，
        // 说明用户查的不是本机 IP —— 直接说明原因，绝不能把本机数据冒充成目标 IP 的结果。
        if (returnedIp == null) {
            return result(ProviderStatus.ERROR, error = "服务未返回 IP")
        }
        if (!returnedIp.equals(queriedIp, ignoreCase = true)) {
            // 用 SKIPPED 而不是 ERROR：这不是出错，而是该源对“非本机 IP”没有能力，
            // 不该计入失败数，也不该让用户以为网络有问题。
            return result(
                ProviderStatus.SKIPPED,
                ip = returnedIp,
                message = "该接口只能查询本机 IP，本次已跳过",
            )
        }

        val fraudScore = numberField(map, "fraudScore")?.toDouble()?.toInt()?.coerceIn(0, 100)
        val residential = boolField(map, "isResidential")
        val broadcast = boolField(map, "isBroadcast")
        val asnValue = numberField(map, "asn")?.toInt()?.let { "AS$it" }

        val flags = buildSet {
            // 非住宅 = 机房/商业线路。注意 isResidential=false 也可能表示企业专线，
            // 这里只在明确给出 false 时标记 hosting，且 UI 文案用“非住宅”更准确。
            if (residential == false) add(ProviderFlag.DATACENTER)
            if (broadcast == true) add(ProviderFlag.ABUSER)
        }

        // 来源属性判定（IPPure 是唯一同时给出 isResidential 与 isBroadcast 的免费源）
        val origin = when {
            broadcast == true -> com.zyvioip.app.model.IpOrigin.BROADCAST
            residential == true -> com.zyvioip.app.model.IpOrigin.NATIVE
            residential == false -> com.zyvioip.app.model.IpOrigin.DATACENTER
            else -> null
        }

        return result(
            status = ProviderStatus.SUCCESS,
            ip = returnedIp,
            country = textField(map, "country"),
            countryCode = textField(map, "countryCode"),
            city = textField(map, "city"),
            region = textField(map, "region"),
            company = textField(map, "asOrganization"),
            asn = asnValue,
            riskScore = fraudScore,
            purity = toPurity(fraudScore),
            isResidential = residential,
            isBroadcast = broadcast,
            origin = origin,
            flags = flags,
            message = buildString {
                append("风险分 ${fraudScore ?: "—"}")
                append(" · ")
                append(
                    when (residential) {
                        true -> "住宅/原生 IP"
                        false -> "非住宅（机房或商业线路）"
                        null -> "住宅属性未知"
                    }
                )
                if (broadcast == true) append(" · 广播地址")
            },
        )
    }
}
