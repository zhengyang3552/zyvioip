package com.zyvioip.app.provider

import okhttp3.Request

/** APIVoid: risk score 0..100 plus VPN/proxy/Tor flags. */
class ApiVoidProvider(
    spec: ProviderSpec = ProviderCatalog.byId("apivoid")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("endpoint.apivoid.com") {
            addPathSegments("ipv2/v1/pay-as-you-go/")
            addPathSegment(config.apiKey.trim())
            addQueryParameter("ip", ip)
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val root = json.decodeFromString<Map<String, Any?>>(body)
        if (textField(root, "error") != null && nested(root, "data").isEmpty()) {
            return result(ProviderStatus.ERROR, error = textField(root, "error"))
        }
        val data = nested(root, "data")
        val report = nested(data, "report")
        val security = nested(report, "security")
        val score = numberField(report, "risk_score") ?: numberField(security, "risk_score")
        val flags = buildSet {
            if (boolField(security, "is_vpn") == true || boolField(security, "vpn") == true) add(ProviderFlag.VPN)
            if (boolField(security, "is_proxy") == true || boolField(security, "proxy") == true) add(ProviderFlag.PROXY)
            if (boolField(security, "is_tor") == true || boolField(security, "tor") == true) add(ProviderFlag.TOR)
            if (boolField(security, "is_datacenter") == true) add(ProviderFlag.DATACENTER)
            if (boolField(security, "is_abuser") == true) add(ProviderFlag.ABUSER)
        }
        return result(
            status = if (score != null) ProviderStatus.SUCCESS else ProviderStatus.ERROR,
            ip = textField(data, "ip") ?: queriedIp,
            country = textField(report, "country_code") ?: textField(report, "country"),
            city = textField(report, "city"),
            region = textField(report, "region"),
            company = textField(report, "isp"),
            asn = textField(report, "asn"),
            purity = toPurity(score?.toDouble()),
            flags = flags,
            error = if (score == null) "服务未返回评分" else null,
        )
    }
}
