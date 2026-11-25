package com.zyvioip.app.provider

import okhttp3.Request

/** IPGeolocation: location and security object from api.ipgeolocation.io. */
class IpGeolocationProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipgeolocation")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("api.ipgeolocation.io") {
            addPathSegments("ipgeo")
            addQueryParameter("apiKey", config.apiKey.trim())
            addQueryParameter("ip", ip)
            addQueryParameter("fields", "ip,country_name,country_code2,city,state_prov,organization,asn,security")
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        if (textField(map, "message") != null && textField(map, "ip") == null) {
            return result(ProviderStatus.ERROR, error = textField(map, "message"))
        }
        val security = nested(map, "security")
        val flags = buildSet {
            if (boolField(security, "is_vpn") == true || boolField(security, "vpn") == true) add(ProviderFlag.VPN)
            if (boolField(security, "is_proxy") == true || boolField(security, "proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(security, "is_tor") == true || boolField(security, "tor") == true) add(ProviderFlag.TOR)
            if (boolField(security, "is_datacenter") == true) add(ProviderFlag.DATACENTER)
            if (boolField(security, "is_mobile") == true) add(ProviderFlag.MOBILE)
        }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "ip") ?: queriedIp,
            country = textField(map, "country_name"),
            countryCode = textField(map, "country_code2"),
            city = textField(map, "city"),
            region = textField(map, "state_prov"),
            company = textField(map, "organization"),
            asn = textField(map, "asn"),
            flags = flags,
        )
    }
}
