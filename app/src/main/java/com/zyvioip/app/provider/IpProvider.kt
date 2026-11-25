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
            defaultActive = true,
            scorable = true,
            signalKind = ProviderSignalKind.PURITY,
            note = "Proxy probability score. A contact email is required by the service.",
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
            id = "ipdatafree",
            name = "ipdata.free",
            auth = ProviderAuth.NONE,
            requiresKey = false,
            defaultActive = true,
            scorable = false,
            signalKind = ProviderSignalKind.FLAG,
            note = "Free city-level/ASN info; no risk score.",
            configLabel = "API Key",
        ),
        ProviderSpec(
            id = "ipapisanon",
            name = "ipapi.is (anon)",
            auth = ProviderAuth.NONE,
            defaultActive = true,
            scorable = true,
            signalKind = ProviderSignalKind.PURITY,
            note = "Anonymous proxy score and location, no key required.",
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
