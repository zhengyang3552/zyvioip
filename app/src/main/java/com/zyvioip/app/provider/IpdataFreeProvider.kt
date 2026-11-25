package com.zyvioip.app.provider

import okhttp3.Request

/** ipdata.co free tier: strict HTTPS, thin response, key from config. */
class IpdataFreeProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipdatafree")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("api.ipdata.co") {
            addPathSegment(ip)
            addQueryParameter("api-key", config.apiKey.trim())
            addQueryParameter("fields", "ip,city,region,country_name,country_code,asn,organisation,threat,is_proxy,is_vpn,is_tor,message")
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = json.decodeFromString<Map<String, Any?>>(body)
        if (textField(map, "message") != null && textField(map, "ip") == null) {
            return result(ProviderStatus.ERROR, error = textField(map, "message"))
        }
        val threat = nested(map, "threat")
        val flags = buildSet {
            if (boolField(map, "is_proxy") == true || boolField(threat, "is_proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(map, "is_vpn") == true || boolField(threat, "is_vpn") == true) add(ProviderFlag.VPN)
            if (boolField(map, "is_tor") == true || boolField(threat, "is_tor") == true) add(ProviderFlag.TOR)
            if (boolField(threat, "is_known_abuser") == true || boolField(threat, "is_attacker") == true) add(ProviderFlag.ABUSER)
        }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "ip") ?: queriedIp,
            country = textField(map, "country_name"),
            countryCode = textField(map, "country_code"),
            city = textField(map, "city"),
            region = textField(map, "region"),
            company = textField(map, "organisation"),
            asn = textField(map, "asn"),
            flags = flags,
        )
    }
}
