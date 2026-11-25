package com.zyvioip.app.provider

import okhttp3.Request

/** ipdata.free: no-key information card; returns location/ASN only, never a purity score. */
class IpdataFreeProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipdatafree")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("ipdata.free") { addPathSegment(ip) }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        val ip = textField(map, "ip") ?: queriedIp
        return if (isValidOutcomeIp(ip)) {
            result(
                status = ProviderStatus.SUCCESS,
                ip = ip,
                country = textField(map, "country"),
                city = textField(map, "city"),
                region = textField(map, "region"),
                company = textField(map, "org"),
                asn = textField(map, "asn"),
                message = "仅信息源，不参与评分",
            )
        } else {
            result(ProviderStatus.ERROR, error = textField(map, "message") ?: "服务未返回有效 IP")
        }
    }

    private fun isValidOutcomeIp(value: String): Boolean =
        value.contains('.') || value.contains(':')
}
