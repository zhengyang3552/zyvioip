package com.zyvioip.app.provider

import okhttp3.Request

/**
 * ipdata.co 的免 key 信息源：只返回国家/城市/ASN/运营商，没有风险评分。
 *
 * 注意：ipdata.co 免费额度需要 API key 才能拿到完整字段，无 key 时只给极少数字段且容易 403。
 * 因此解析必须容忍缺字段，并且在 403/401 时给出明确提示，而不是显示成“成功但空卡片”。
 */
class IpdataFreeProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipdatafree")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("api.ipdata.co") {
            addPathSegment(ip)
            // 只要地理信息，减少响应体积
            addQueryParameter("fields", "ip,country_name,country_code,city,region,organisation,asn")
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        // ipdata 的错误响应形如 {"message": "..."}，没有 ip 字段
        val message = textField(map, "message")
        val ip = textField(map, "ip") ?: queriedIp
        if (message != null && textField(map, "ip") == null) {
            return result(ProviderStatus.ERROR, error = message)
        }
        return if (isValidOutcomeIp(ip)) {
            result(
                status = ProviderStatus.SUCCESS,
            rawFields = flattenForDisplay(map),
                ip = ip,
                country = textField(map, "country_name") ?: textField(map, "country"),
                countryCode = textField(map, "country_code"),
                city = textField(map, "city"),
                region = textField(map, "region"),
                // ipdata 用 organisation / org 两种拼写
                company = textField(map, "organisation") ?: textField(map, "org"),
                asn = textField(map, "asn"),
                message = "仅信息源，不参与评分",
            )
        } else {
            result(ProviderStatus.ERROR, error = "服务未返回有效 IP")
        }
    }

    private fun isValidOutcomeIp(value: String): Boolean =
        value.contains('.') || value.contains(':')
}
