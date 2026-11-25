package com.zyvioip.app.provider

import okhttp3.Request

/**
 * ipwho.is：完全免密钥的地理/ASN 信息源。
 *
 * 实测响应（`?fields=...` 精简后）：
 * ```json
 * {"ip":"8.8.8.8","success":true,"country":"United States","country_code":"US",
 *  "region":"California","city":"San Jose",
 *  "connection":{"asn":15169,"org":"Google LLC","isp":"Google LLC"}}
 * ```
 * 失败时返回 `{"success":false,"message":"..."}`。
 * 该源不提供风险评分，只作信息卡片。
 */
class IpWhoIsProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipwhois")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("ipwho.is") {
            addPathSegment(ip)
            addQueryParameter("fields", "ip,success,country,country_code,region,city,connection")
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        if (map.isEmpty()) {
            return result(ProviderStatus.ERROR, error = "响应解析失败，请稍后重试")
        }
        if (boolField(map, "success") == false) {
            return result(ProviderStatus.ERROR, error = textField(map, "message") ?: "查询失败")
        }
        val connection = nested(map, "connection")
        val asnValue = numberField(connection, "asn")?.toInt()?.let { "AS$it" }
        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "ip") ?: queriedIp,
            country = textField(map, "country"),
            countryCode = textField(map, "country_code"),
            city = textField(map, "city"),
            region = textField(map, "region"),
            company = textField(connection, "org") ?: textField(connection, "isp"),
            asn = asnValue,
            message = "仅信息源，不参与评分",
        )
    }
}
