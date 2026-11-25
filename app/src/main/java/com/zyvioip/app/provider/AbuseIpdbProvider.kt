package com.zyvioip.app.provider

import okhttp3.Request

/** AbuseIPDB: abuse confidence 0..100 becomes purity 100..0. */
class AbuseIpdbProvider(
    spec: ProviderSpec = ProviderCatalog.byId("abuseipdb")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request =
        url("api.abuseipdb.com") {
            addPathSegments("api/v2/check")
            addQueryParameter("ipAddress", ip)
            addQueryParameter("maxAgeInDays", "90")
            addQueryParameter("verbose", "")
        }.let { endpoint ->
            Request.Builder().url(endpoint).get()
                .header("Key", config.apiKey.trim())
                .header("Accept", "application/json")
                .build()
        }

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val data = nested(parseObject(json, body), "data")
        val score = numberField(data, "abuseConfidenceScore")
        val flags = buildSet {
            if ((score?.toInt() ?: 0) >= 50) add(ProviderFlag.ABUSER)
            if (boolField(data, "isTor") == true) add(ProviderFlag.TOR)
            // usageType 才是 AbuseIPDB 判断机房用的字段（"Data Center/Web Hosting/Transit"）。
            // 原来误把 isp 当布尔量判断，永远得不到结果，数据中心标记从来没生效过。
            val usage = textField(data, "usageType").orEmpty().lowercase()
            if (usage.contains("data center") || usage.contains("hosting") || usage.contains("transit")) {
                add(ProviderFlag.DATACENTER)
            }
        }
        return result(
            status = if (score != null) ProviderStatus.SUCCESS else ProviderStatus.ERROR,
            ip = textField(data, "ipAddress") ?: queriedIp,
            country = textField(data, "countryCode"),
            city = textField(data, "city"),
            region = textField(data, "region"),
            company = textField(data, "isp"),
            asn = textField(data, "asn"),
            purity = toPurity(score),
            flags = flags,
            error = if (score == null) "服务未返回评分" else null,
        )
    }
}
