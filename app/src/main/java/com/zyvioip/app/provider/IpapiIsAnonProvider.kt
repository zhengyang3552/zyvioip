package com.zyvioip.app.provider

import okhttp3.Request

/**
 * ipapi.is 免费（无需 key）端点。
 *
 * 实测该端点返回的是**扁平结构**，没有 location / asn / company 嵌套，也没有 abuse_score：
 *
 * ```json
 * {"ip":"8.8.8.8","is_bogon":false,"company":"Google LLC","asn":"AS15169 Google LLC",
 *  "city":"Mountain View","region":"California","country":"United States",
 *  "lat":37.38605,"lon":-122.08385,"timezone":"America/Los_Angeles"}
 * ```
 *
 * 旧实现按嵌套字段去取，company/asn/city/country 全部取空，
 * 而且它本身没有任何风险评分，却被标成 scorable —— 因此这里改为
 * 只当信息源使用（解析扁平字段，不产出 purity）。
 */
class IpapiIsAnonProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipapisanon")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("api.ipapi.is") {
            addQueryParameter("q", ip)
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)

        // 错误响应：{"error": "..."} 且没有 ip
        textField(map, "error")?.let { err ->
            if (textField(map, "ip") == null) {
                return result(ProviderStatus.ERROR, error = err)
            }
        }

        val ip = textField(map, "ip") ?: queriedIp
        if (textField(map, "ip") == null && map.isEmpty()) {
            return result(ProviderStatus.ERROR, error = "响应解析失败，请稍后重试")
        }
        // 广播/保留地址，ipapi.is 会显式标记
        if (boolField(map, "is_bogon") == true) {
            return result(ProviderStatus.ERROR, ip = ip, error = "非公网 IP（内网/保留地址）")
        }

        // "AS15169 Google LLC" -> asn = "AS15169"
        val asnRaw = textField(map, "asn")
        val asnNumber = asnRaw?.substringBefore(' ', "")?.takeIf { it.startsWith("AS", ignoreCase = true) }
        val asnName = asnRaw?.substringAfter(' ', "")?.takeIf { it.isNotBlank() }

        return result(
            status = ProviderStatus.SUCCESS,
            rawFields = flattenForDisplay(map),
            ip = ip,
            country = textField(map, "country"),
            countryCode = countryCodeFromName(textField(map, "country")),
            city = textField(map, "city"),
            region = textField(map, "region"),
            // 免费版用 company（`asn` 字段里也带运营商名，作为兜底）
            company = textField(map, "company") ?: asnName,
            asn = asnNumber ?: asnRaw,
            // 免费端点没有风险评分，不产出 purity
            purity = null,
            // 免费端点也不提供 VPN/proxy/Tor 标记，不要凭空臆测
            flags = emptySet(),
            message = "免费端点仅提供地理信息，不参与评分",
        )
    }

    /** 免费端点只给国家全名，这里映射常用国家的 alpha-2，用于显示国旗。 */
    private fun countryCodeFromName(name: String?): String? = when (name?.trim()?.lowercase()) {
        "china" -> "CN"
        "united states" -> "US"
        "japan" -> "JP"
        "south korea", "korea, republic of" -> "KR"
        "united kingdom" -> "GB"
        "germany" -> "DE"
        "france" -> "FR"
        "netherlands" -> "NL"
        "singapore" -> "SG"
        "hong kong" -> "HK"
        "taiwan" -> "TW"
        "canada" -> "CA"
        "australia" -> "AU"
        "india" -> "IN"
        "russia" -> "RU"
        "brazil" -> "BR"
        else -> null
    }
}
