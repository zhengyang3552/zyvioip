package com.zyvioip.app.provider

import okhttp3.Request

/** ip-api.com free tier: HTTP-only endpoint, location plus proxy/hosting flags. */
class IpApiComProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipapicom")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = Request.Builder()
        .url(
            okhttp3.HttpUrl.Builder().scheme("http").host("ip-api.com").apply {
                addPathSegment("json")
                addPathSegment(ip)
                // org 是真正的运营方，isp 往往只是线路商（例如 RackNerd 的 isp 显示 HostPapa），
                // asname 能给出 AS 名称，这几个都取回来展示更准确。
                addQueryParameter("fields", "status,message,country,countryCode,regionName,city,isp,org,as,asname,proxy,hosting,mobile,query")
            }.build()
        )
        .get()
        .build()

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
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
            // 优先 org（真实运营方），没有再用 isp
            company = textField(map, "org") ?: textField(map, "isp"),
            asn = textField(map, "as"),
            flags = flags,
        )
    }
}
