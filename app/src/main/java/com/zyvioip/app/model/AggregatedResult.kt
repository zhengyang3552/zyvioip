package com.zyvioip.app.model

import com.zyvioip.app.provider.ProviderOutcome

/** A provider spec paired with the provider slice's current outcome. */
data class ProviderState(
    val spec: ProviderSpec,
    val outcome: ProviderOutcome,
)

/** Combined view returned by the lookup repository. */
data class AggregatedResult(
    val ip: String,
    val signals: IpSignals = IpSignals(ip = ip),
    val providerStates: List<ProviderState> = emptyList(),
    val purity: Int? = null,
    val type: IpType = IpType.UNKNOWN,
    val completedAtMillis: Long = System.currentTimeMillis(),
)

enum class IpType(val label: String) {
    VPN("VPN"),
    PROXY("代理"),
    TOR("Tor"),
    DATACENTER("数据中心"),
    MOBILE("移动网络"),
    UNKNOWN("未知"),
}
