package com.zyvioip.app.provider

import kotlinx.serialization.Serializable




/** Credentials supplied by the user. Never serialise these to logs or analytics. */
@Serializable
data class ProviderConfig(
    val providerId: String,
    val apiKey: String = "",
    val email: String = "",
) {
    val hasEmail: Boolean get() = email.isNotBlank()
    val hasKey: Boolean get() = apiKey.isNotBlank()
    val apiKeyValue: String get() = apiKey
}





object ProviderFlag {
    const val VPN = "vpn"
    const val PROXY = "proxy"
    const val TOR = "tor"
    const val DATACENTER = "datacenter"
    const val ABUSER = "abuser"
    const val MOBILE = "mobile"
}

@Serializable
data class ProviderSpec(
    val id: String,
    val name: String,
    val auth: ProviderAuth = ProviderAuth.NONE,
    val defaultActive: Boolean = false,
    val requiresKey: Boolean = false,
    val requiresEmail: Boolean = false,
    val scorable: Boolean = true,
    val note: String = "",
    val configLabel: String = "",
    val signalKind: ProviderSignalKind = ProviderSignalKind.INFO,
)

@Serializable
enum class ProviderAuth { NONE, API_KEY, EMAIL }

/** Static description of every supported upstream service. */
object ProviderCatalog {
    val all: List<ProviderSpec> = listOf(
        ProviderSpec(
            id = "getipintel",
            name = "GetIPIntel",
            auth = ProviderAuth.EMAIL,
            requiresEmail = true,
            // 服务强制要求联系邮箱，否则返回 -6。
            // 默认启用会让新装用户一进来就看到一张“需要配置”的红卡，改为默认关闭，
            // 配好邮箱后在“添加供应商”里加进来。
            defaultActive = false,
            scorable = true,
            signalKind = ProviderSignalKind.PURITY,
            note = "代理概率评分。必须填写可收发的联系邮箱，否则服务拒绝查询。",
            configLabel = "联系邮箱",
        ),
        ProviderSpec(
            id = "ipapicom",
            name = "ip-api.com",
            auth = ProviderAuth.NONE,
            defaultActive = true,
            scorable = false,
            signalKind = ProviderSignalKind.FLAG,
            note = "Free geolocation and proxy/hosting flags (HTTP, no key).",
        ),
        ProviderSpec(
            id = "ipwhois",
            name = "ipwho.is",
            auth = ProviderAuth.NONE,
            defaultActive = true,
            scorable = false,
            signalKind = ProviderSignalKind.FLAG,
            note = "完全免密钥：地理位置 + ASN，无限流门槛。",
        ),
        ProviderSpec(
            id = "ipdatafree",
            name = "ipdata.co",
            auth = ProviderAuth.API_KEY,
            // 实测 api.ipdata.co 无 key 一律返回 401（"You have not provided a valid API Key"），
            // 之前标成 requiresKey=false 且默认启用，导致默认状态下一张卡片必然“请求被拒绝 (401)”。
            requiresKey = true,
            defaultActive = false,
            scorable = false,
            signalKind = ProviderSignalKind.FLAG,
            note = "需要 ipdata.co 免费额度的 API Key（无 key 会返回 401）。",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "ipapisanon",
            name = "ipapi.is (anon)",
            auth = ProviderAuth.NONE,
            defaultActive = true,
            // 免费端点实测只返回地理信息（无 abuse_score / VPN / proxy 标记），
            // 之前标成 scorable=true 会让“纯净度”分母里混进一个永远拿不到分的源。
            scorable = false,
            signalKind = ProviderSignalKind.INFO,
            note = "免密钥免费端点：仅提供国家/城市/ASN，不提供风险评分。",
        ),
        ProviderSpec(
            id = "ipapisis",
            name = "ipapi.is",
            auth = ProviderAuth.API_KEY,
            requiresKey = true,
            defaultActive = false,
            scorable = true,
            signalKind = ProviderSignalKind.PURITY,
            note = "Requires an ipapi.is API key.",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "abuseipdb",
            name = "AbuseIPDB",
            auth = ProviderAuth.API_KEY,
            requiresKey = true,
            defaultActive = false,
            scorable = true,
            signalKind = ProviderSignalKind.PURITY,
            note = "Abuse confidence score; requires an API key.",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "ipqs",
            name = "IPQualityScore",
            auth = ProviderAuth.API_KEY,
            requiresKey = true,
            defaultActive = false,
            scorable = true,
            signalKind = ProviderSignalKind.PURITY,
            note = "Fraud score, VPN/proxy/Tor detection; requires an API key.",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "apivoid",
            name = "APIVoid",
            auth = ProviderAuth.API_KEY,
            requiresKey = true,
            defaultActive = false,
            scorable = true,
            signalKind = ProviderSignalKind.PURITY,
            note = "Security score and threat flags; requires an API key.",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "ipdata",
            name = "ipdata.co",
            auth = ProviderAuth.API_KEY,
            requiresKey = true,
            defaultActive = false,
            scorable = false,
            signalKind = ProviderSignalKind.FLAG,
            note = "Full ipdata.co API; requires a paid-tier API key.",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "ipgeolocation",
            name = "IPGeolocation",
            auth = ProviderAuth.API_KEY,
            requiresKey = true,
            defaultActive = false,
            scorable = false,
            signalKind = ProviderSignalKind.FLAG,
            note = "Geolocation and security fields; requires an API key.",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "whoisxml",
            name = "WhoisXML API",
            auth = ProviderAuth.API_KEY,
            requiresKey = true,
            defaultActive = false,
            scorable = false,
            signalKind = ProviderSignalKind.INFO,
            note = "IP geolocation and ASN details; requires an API key.",
            configLabel = "API Key",
        ),
    )

    val defaultActiveIds: Set<String> = all.filter { it.defaultActive }.map { it.id }.toSet()

    fun byId(id: String): ProviderSpec? = all.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun create(spec: ProviderSpec, config: ProviderConfig? = null): IpProvider = when (spec.id) {
        "getipintel" -> GetIpIntelProvider(spec, config)
        "ipapicom" -> IpApiComProvider(spec, config)
        "ipdatafree" -> IpdataFreeProvider(spec, config)
        "ipwhois" -> IpWhoIsProvider(spec, config)
        "ipapisanon" -> IpapiIsAnonProvider(spec, config)
        "ipapisis" -> IpapiIsProvider(spec, config)
        "abuseipdb" -> AbuseIpdbProvider(spec, config)
        "ipqs" -> IpqsProvider(spec, config)
        "apivoid" -> ApiVoidProvider(spec, config)
        "ipdata" -> IpDataProvider(spec, config)
        "ipgeolocation" -> IpGeolocationProvider(spec, config)
        "whoisxml" -> WhoisXmlProvider(spec, config)
        else -> throw IllegalArgumentException("Unknown provider: ${spec.id}")
    }
}
