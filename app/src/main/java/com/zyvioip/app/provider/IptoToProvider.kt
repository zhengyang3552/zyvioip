package com.zyvioip.app.provider

import com.zyvioip.app.model.IpOrigin
import okhttp3.Request

/**
 * ipto.to —— 免密钥 IP 属性 + 风险情报（实测 2026-11）。
 *
 * 接口：`https://ipto.to/v1/ip/{ip}`
 *
 * 实测响应（185.220.101.45，TOR 相关出口）：
 * ```json
 * {"ip":"185.220.101.45","asn":60729,"asn_org":"Stiftung Erneuerbare Freiheit",
 *  "asn_type":"hosting","country_code":"US","city":"Delaware","state":"Ohio",
 *  "is_datacenter":true,"is_mobile":false,"is_residential":false,
 *  "is_proxy":false,"is_vpn":true,"is_tor":false,"is_abuser":true,
 *  "abuse_score":3,"risk_tier":"medium","threat_type":"abuser",
 *  "connection_type":"datacenter"}
 * ```
 *
 * 注意：
 * - `abuse_score` / `risk_tier` / `threat_type` 只在**有风险记录**的 IP 上出现，
 *   普通 IP 返回 null，不能当成 0 分。
 * - `is_residential` 覆盖不全（国内家宽实测为 false 且 connection_type=unknown），
 *   只采信 `is_residential=true`，false 不推断成机房。
 */
class IptoToProvider(
    spec: ProviderSpec = ProviderCatalog.byId("iptoto")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("ipto.to") {
            addPathSegments("v1/ip")
            addPathSegment(ip)
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        if (map.isEmpty()) {
            return result(ProviderStatus.ERROR, error = "响应解析失败，请稍后重试")
        }
        textField(map, "error")?.let { return result(ProviderStatus.ERROR, error = it) }

        val bogon = boolField(map, "bogon") == true
        if (bogon) {
            return result(
                ProviderStatus.ERROR,
                ip = textField(map, "ip") ?: queriedIp,
                error = "非公网 IP（内网/保留地址）",
            )
        }

        val isDatacenter = boolField(map, "is_datacenter") == true
        val isVpn = boolField(map, "is_vpn") == true
        val isTor = boolField(map, "is_tor") == true
        val isProxy = boolField(map, "is_proxy") == true
        val isAbuser = boolField(map, "is_abuser") == true
        val isMobile = boolField(map, "is_mobile") == true
        val isResidential = boolField(map, "is_residential") == true

        val flags = buildSet {
            if (isVpn) add(ProviderFlag.VPN)
            if (isTor) add(ProviderFlag.TOR)
            if (isProxy) add(ProviderFlag.PROXY)
            if (isDatacenter) add(ProviderFlag.DATACENTER)
            if (isAbuser) add(ProviderFlag.ABUSER)
            if (isMobile) add(ProviderFlag.MOBILE)
        }

        // 风险分：只有服务给了 abuse_score 才算有分；没有记录 = 没有评分，不是 0 分
        val abuse = numberField(map, "abuse_score")?.toDouble()?.toInt()
        val riskTier = textField(map, "risk_tier")
        val threatType = textField(map, "threat_type")

        val origin = when {
            isResidential -> IpOrigin.NATIVE
            isDatacenter -> IpOrigin.DATACENTER
            isMobile -> IpOrigin.MOBILE
            else -> null
        }

        val asnValue = numberField(map, "asn")?.toInt()?.let { "AS$it" }

        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "ip") ?: queriedIp,
            country = textField(map, "country_name"),
            countryCode = textField(map, "country_code"),
            city = textField(map, "city"),
            region = textField(map, "state"),
            company = textField(map, "asn_org") ?: textField(map, "asn_name"),
            asn = asnValue,
            riskScore = abuse?.coerceIn(0, 100),
            purity = abuse?.let { 100 - it.coerceIn(0, 100) },
            isResidential = boolField(map, "is_residential"),
            origin = origin,
            flags = flags,
            message = buildString {
                append(
                    when {
                        isTor -> "Tor 出口节点"
                        isVpn -> "VPN 出口"
                        isProxy -> "代理"
                        isDatacenter -> "机房/数据中心"
                        isResidential -> "住宅线路"
                        else -> "未发现风险特征"
                    }
                )
                if (abuse != null) append(" · 滥用记录 $abuse/100")
                if (riskTier != null) append(" · 风险等级 $riskTier")
                if (threatType != null && threatType != "none") append(" · 威胁类型 $threatType")
            },
        )
    }
}
