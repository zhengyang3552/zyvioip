package com.zyvioip.app.provider

import okhttp3.HttpUrl
import okhttp3.Request

/**
 * ip-api.com 免费版。
 *
 * 免费版只支持 **HTTP**（https 会返回 403 "SSL unavailable for this endpoint"），
 * 因此需要在 network_security_config 里单独放行该域名的明文流量。
 *
 * 实测返回（2026-11，全部字段）：
 * ```json
 * {"status":"success","country":"United States","countryCode":"US",
 *  "region":"NY","regionName":"New York","city":"Buffalo","zip":"14205",
 *  "lat":42.8864,"lon":-78.8784,"timezone":"America/New_York",
 *  "isp":"HostPapa","org":"RackNerd LLC","as":"AS36352 HostPapa",
 *  "asname":"AS-COLOCROSSING","reverse":"...host.colocrossing.com",
 *  "mobile":false,"proxy":false,"hosting":true}
 * ```
 *
 * 关键点：
 *  - `org` 是真实运营方（RackNerd LLC），`isp` 常只是线路商（HostPapa），两者都要保留；
 *  - `as` 形如 "AS36352 HostPapa"，需拆出纯 ASN "AS36352"；
 *  - `hosting` / `proxy` / `mobile` / `reverse` 足以判定机房与代理特征。
 */
class IpApiComProvider(
    spec: ProviderSpec = ProviderCatalog.byId("ipapicom")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request {
        val url: HttpUrl = HttpUrl.Builder()
            .scheme("http")
            .host("ip-api.com")
            .addPathSegment("json")
            .addPathSegment(ip)
            // 一次性取全：地理、运营商、AS、反查域名、标记
            .addQueryParameter(
                "fields",
                "status,message,country,countryCode,region,regionName,city,zip," +
                    "timezone,isp,org,as,asname,reverse,proxy,hosting,mobile,query",
            )
            .build()
        return Request.Builder().url(url).get().build()
    }

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val map = parseObject(json, body)
        if (textField(map, "status")?.equals("fail", ignoreCase = true) == true) {
            return result(ProviderStatus.ERROR, error = textField(map, "message") ?: "查询失败")
        }

        val hosting = boolField(map, "hosting") == true
        val proxy = boolField(map, "proxy") == true
        val mobile = boolField(map, "mobile") == true

        val isp = textField(map, "isp")
        val org = textField(map, "org")

        // 兜底：有些机房 IP 的 hosting 字段是 false（例如 220.181.38.148，
        // org 明确写着 "IDC, China Telecommunications Corporation" 但 hosting=false）。
        // 这里用组织名/AS 名做一次关键词判定，避免把它误判成住宅 IP。
        val orgText = buildString {
            listOfNotNull(isp, org, textField(map, "asname")).forEach {
                if (it.isNotBlank()) append(it.lowercase()).append(' ')
            }
        }
        val looksLikeHosting = listOf(
            "hosting", "data center", "datacenter", "idc", "colocation", "colo",
            "cloud", "vps", "server", "dedicated", "机房",
        ).any { orgText.contains(it) }

        val hostingOrIdc = hosting || looksLikeHosting
        val flags = buildSet {
            if (proxy) add(ProviderFlag.PROXY)
            if (hostingOrIdc) add(ProviderFlag.DATACENTER)
            if (mobile) add(ProviderFlag.MOBILE)
        }
        // "AS36352 HostPapa" -> "AS36352"
        val asRaw = textField(map, "as")
        val asnNumber = asRaw?.trim()?.split(' ')?.firstOrNull()?.takeIf {
            it.startsWith("AS", ignoreCase = true)
        }

        val verdict = buildString {
            append(
                when {
                    hostingOrIdc && proxy -> "机房 IP，且带代理特征"
                    hostingOrIdc -> "机房/数据中心 IP"
                    proxy -> "检测到代理特征"
                    mobile -> "移动网络"
                    else -> "未标记为机房或代理"
                }
            )
            org?.takeIf { it.isNotBlank() }?.let { append(" · 运营方 $it") }
            textField(map, "reverse")?.let { append(" · 反查 $it") }
        }

        // ip-api.com 没有 isResidential，只能从 hosting/proxy/mobile 推断
        val origin = when {
            hostingOrIdc -> com.zyvioip.app.model.IpOrigin.DATACENTER
            mobile -> com.zyvioip.app.model.IpOrigin.MOBILE
            // 既非机房也非移动 -> 大致是普通宽带，但无法区分住宅与企业，标 UNKNOWN 更诚实
            else -> null
        }

        return result(
            status = ProviderStatus.SUCCESS,
            ip = textField(map, "query") ?: queriedIp,
            country = textField(map, "country"),
            countryCode = textField(map, "countryCode"),
            city = textField(map, "city"),
            region = textField(map, "regionName") ?: textField(map, "region"),
            // 优先真实运营方 org，其次才是线路商 isp
            company = org?.takeIf { it.isNotBlank() } ?: isp,
            asn = asnNumber ?: asRaw,
            flags = flags,
            // 没有 hosting/proxy 时视为住宅/非机房线路
            isResidential = !hostingOrIdc && !proxy,
            origin = origin,
            message = verdict,
        )
    }
}
