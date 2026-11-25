package com.zyvioip.app.provider

import com.zyvioip.app.model.IpOrigin
import okhttp3.Request

/**
 * proxycheck.io —— 免密钥代理/VPN/Tor 检测 + risk 分（实测 2026-11）。
 *
 * 接口：`https://proxycheck.io/v2/{ip}?vpn=1&asn=1&risk=1`
 *
 * 实测响应结构比较特殊：**IP 本身是顶层 key**，不是固定字段名。
 * ```json
 * {"status":"ok",
 *  "185.220.101.45":{
 *    "asn":"AS60729","provider":"Stiftung...","organisation":"ForPrivacyNET",
 *    "country":"Germany","isocode":"DE","city":"Berlin",
 *    "proxy":"yes","type":"TOR","risk":100,
 *    "devices":{"address":1,"subnet":152}}}
 * ```
 * 注意 `proxy` 是字符串 "yes"/"no"（不是布尔），`risk` 是数字。
 * 失败时返回 `{"status":"error","message":"..."}`。
 */
class ProxyCheckProvider(
    spec: ProviderSpec = ProviderCatalog.byId("proxycheck")!!,
    config: ProviderConfig? = null,
) : IpProvider(spec, config) {

    override fun buildRequest(ip: String): Request = get(
        url("proxycheck.io") {
            addPathSegments("v2")
            addPathSegment(ip)
            addQueryParameter("vpn", "1")
            addQueryParameter("asn", "1")
            addQueryParameter("risk", "1")
        }
    )

    override fun parse(body: String, queriedIp: String): ProviderOutcome {
        val root = parseObject(json, body)
        if (root.isEmpty()) {
            return result(ProviderStatus.ERROR, error = "响应解析失败，请稍后重试")
        }
        if (textField(root, "status")?.equals("error", ignoreCase = true) == true) {
            return result(ProviderStatus.ERROR, error = textField(root, "message") ?: "查询失败")
        }

        // 数据挂在以 IP 为名的 key 下；取不到就退化成“第一个非 status 的对象”。
        // parseObject 已经把嵌套对象转成 Map<String, Any?>，直接强转即可。
        val entry = (root[queriedIp] as? Map<String, Any?>)
            ?: root.entries
                .firstOrNull { it.key != "status" && it.value is Map<*, *> }
                ?.value
                ?.let { it as? Map<String, Any?> }

        if (entry == null) {
            return result(ProviderStatus.ERROR, error = "服务未返回该 IP 的数据")
        }

        // proxy 字段是字符串 "yes"/"no"
        val isProxy = boolField(entry, "proxy") == true
        val type = textField(entry, "type")?.uppercase()
        val risk = numberField(entry, "risk")?.toDouble()?.toInt()?.coerceIn(0, 100)

        val isTor = type == "TOR"
        val isVpn = type == "VPN" || isTor
        val isPublicProxy = type?.contains("PROXY") == true || (isProxy && !isTor && !isVpn)

        val flags = buildSet {
            if (isTor) add(ProviderFlag.TOR)
            if (isVpn && !isTor) add(ProviderFlag.VPN)
            if (isPublicProxy) add(ProviderFlag.PROXY)
            if (risk != null && risk >= 66) add(ProviderFlag.ABUSER)
        }

        val origin = when (type) {
            // 商业线路归到 BUSINESS；住宅类型才是住宅
            "RESIDENTIAL" -> IpOrigin.RESIDENTIAL
            "BUSINESS" -> IpOrigin.BUSINESS
            else -> null
        }

        return result(
            status = ProviderStatus.SUCCESS,
            ip = queriedIp,
            country = textField(entry, "country"),
            countryCode = textField(entry, "isocode"),
            city = textField(entry, "city"),
            region = textField(entry, "region"),
            company = textField(entry, "organisation") ?: textField(entry, "provider"),
            asn = textField(entry, "asn"),
            riskScore = risk,
            purity = risk?.let { 100 - it },
            origin = origin,
            flags = flags,
            message = buildString {
                append(
                    when {
                        isTor -> "Tor 出口节点"
                        isVpn -> "VPN 出口"
                        isPublicProxy -> "公开代理"
                        else -> "未检测到代理特征"
                    }
                )
                if (type != null) append(" · 类型 $type")
                if (risk != null) append(" · 风险 $risk/100")
                textField(entry, "hostname")?.let { append(" · 反查 $it") }
            },
        )
    }
}
