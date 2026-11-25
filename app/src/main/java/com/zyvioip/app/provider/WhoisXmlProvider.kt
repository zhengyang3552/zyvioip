package com.zyvioip.app.provider

import okhttp3.Request

/** WhoisXML IP geolocation: location and ASN details, no abuse score. */
class WhoisXmlProvider(
    spec: ProviderSpec = ProviderCatalog.byId("whoisxml")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("ip-geolocation.whoisxmlapi.com") {
            addPathSegment("api")
            addPathSegment("v1")
            addQueryParameter("apiKey", config.apiKey.trim())
            addQueryParameter("ipAddress", ip)
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = json.decodeFromString<Map<String, Any?>>(body)
        if (textField(map, "errorMessage") != null && nested(map, "ip").isEmpty()) {
            return result(ProviderStatus.ERROR, error = textField(map, "errorMessage"))
        }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "ip") ?: queriedIp,
            country = textField(map, "country"),
            countryCode = textField(map, "countryCode"),
            city = textField(map, "city"),
            region = textField(map, "region"),
            company = textField(map, "isp"),
            asn = textField(map, "asn"),
        )
    }
}
