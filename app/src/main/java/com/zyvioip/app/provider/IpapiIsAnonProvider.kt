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
        val location = nested(map, "location")
        val asn = nested(map, "asn")
        val company = nested(map, "company")
        val score = numberField(map, "abuse_score", "score")?.toDouble()
        val flags = buildSet {
            if (boolField(map, "is_vpn") == true || boolField(map, "proxy") == true) add(ProviderFlag.VPN)
            if (boolField(map, "is_proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(map, "is_tor") == true) add(ProviderFlag.TOR)
            if (boolField(map, "is_datacenter") == true || boolField(asn, "type")?.not() == true) add(ProviderFlag.DATACENTER)
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

    private fun Boolean?.not(): Boolean? = this?.let { !it }
}
