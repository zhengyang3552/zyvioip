package com.zyvioip.app.provider

import okhttp3.Request

/** ip-api.com free tier: HTTP-only endpoint, location plus proxy/hosting flags. */
class IpApiComProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipapicom")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = Request.Builder()
        .url(
            okhttp3.HttpUrl.Builder().scheme("http").host("ip-api.com").addPathSegment("json").addPathSegment(ip)
                .addQueryParameter("fields", "status,message,country,countryCode,regionName,city,isp,as,proxy,hosting,mobile,query")
                .build()
        )
        .get()
        .build()

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = json.decodeFromString<Map<String, Any?>>(body)
        if (textField(map, "status")?.equals("fail", ignoreCase = true) == true) {
            return result(ProviderStatus.ERROR, error = textField(map, "message") ?: "查询失败")
        }
        val flags = buildSet {
            if (boolField(map, "proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(map, "hosting") == true) add(ProviderFlag.DATACENTER)
            if (boolField(map, "mobile") == true) add(ProviderFlag.MOBILE)
        }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "query") ?: queriedIp,
            country = textField(map, "country"),
            countryCode = textField(map, "countryCode"),
            city = textField(map, "city"),
            region = textField(map, "regionName"),
            company = textField(map, "isp"),
            asn = textField(map, "as"),
            flags = flags,
        )
    }
}
