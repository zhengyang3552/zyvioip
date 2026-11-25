package com.zyvioip.app.provider

import okhttp3.Request

/** ipapi.is authenticated endpoint; richer fields and higher limits. */
class IpapiIsProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipapisis")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("api.ipapi.is") {
            addQueryParameter("q", ip)
            addQueryParameter("key", config.apiKey.trim())
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = json.decodeFromString<Map<String, Any?>>(body)
        if (textField(map, "error") != null && textField(map, "ip") == null) {
            return result(ProviderStatus.ERROR, error = textField(map, "error"))
        }
        val location = nested(map, "location")
        val asn = nested(map, "asn")
        val company = nested(map, "company")
        val flags = buildSet {
            if (boolField(map, "is_vpn") == true) add(ProviderFlag.VPN)
            if (boolField(map, "is_proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(map, "is_tor") == true) add(ProviderFlag.TOR)
            if (boolField(map, "is_datacenter") == true) add(ProviderFlag.DATACENTER)
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
            purity = toPurity(numberField(map, "abuse_score", "score")?.toDouble()),
            flags = flags,
        )
    }
}
