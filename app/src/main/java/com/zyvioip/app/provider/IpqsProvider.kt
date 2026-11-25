package com.zyvioip.app.provider

import okhttp3.Request

/** IPQualityScore: fraud score 0..100; VPN/proxy/Tor/geolocation signals. */
class IpqsProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipqs")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("ipqualityscore.com") {
            addPathSegments("api/json")
            addPathSegment(config.apiKey.trim())
            addPathSegment(ip)
            addQueryParameter("strictness", "1")
            addQueryParameter("allow_public_access_points", "true")
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        if (boolField(map, "success") == false) {
            return result(ProviderStatus.ERROR, error = textField(map, "message") ?: "服务拒绝请求")
        }
        val flags = buildSet {
            if (boolField(map, "vpn") == true) add(ProviderFlag.VPN)
            if (boolField(map, "proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(map, "tor") == true) add(ProviderFlag.TOR)
            if (boolField(map, "hosting") == true || boolField(map, "datacenter") == true) add(ProviderFlag.DATACENTER)
            if (boolField(map, "mobile") == true) add(ProviderFlag.MOBILE)
            if (boolField(map, "abuse") == true) add(ProviderFlag.ABUSER)
        }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = queriedIp,
            country = textField(map, "country_code") ?: textField(map, "country"),
            city = textField(map, "city"),
            region = textField(map, "region"),
            company = textField(map, "ISP"),
            asn = textField(map, "ASN"),
            purity = toPurity(numberField(map, "fraud_score")?.toDouble()),
            flags = flags,
        )
    }
}
