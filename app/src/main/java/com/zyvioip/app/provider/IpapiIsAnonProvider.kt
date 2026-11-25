package com.zyvioip.app.provider

import okhttp3.Request

/** ipapi.is anonymous endpoint: free risk score and location without a key. */
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
        if (textField(map, "error") != null && textField(map, "ip") == null) {
            return result(ProviderStatus.ERROR, error = textField(map, "error"))
        }
        val location = nested(map, "location")
        val asn = nested(map, "asn")
        val company = nested(map, "company")
        val score = numberField(map, "abuse_score", "score")?.toDouble()
        val flags = buildSet {
            // 原来 VPN 标记会把 proxy 也算进去，导致类型判定里 VPN/代理 二义。
            if (boolField(map, "is_vpn") == true) add(ProviderFlag.VPN)
            if (boolField(map, "is_proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(map, "is_tor") == true) add(ProviderFlag.TOR)
            if (boolField(map, "is_datacenter") == true) add(ProviderFlag.DATACENTER)
            // asn.type 是 "isp"/"hosting"/"business" 之类的字符串，不是布尔量。
            // 原来写 `boolField(asn,"type")?.not()` 永远是 null，这个分支从未生效。
            val asnType = textField(asn, "type").orEmpty().lowercase()
            if (asnType.contains("hosting") || asnType.contains("data")) add(ProviderFlag.DATACENTER)
            if (boolField(map, "is_mobile") == true) add(ProviderFlag.MOBILE)
        }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "ip") ?: queriedIp,
            country = textField(location, "country") ?: textField(map, "country"),
            countryCode = textField(location, "country_code"),
            city = textField(location, "city"),
            region = textField(location, "state"),
            company = textField(company, "name") ?: textField(asn, "name"),
            asn = textField(asn, "asn"),
            purity = toPurity(score),
            flags = flags,
        )
    }
}
