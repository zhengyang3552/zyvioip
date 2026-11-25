package com.zyvioip.app.util

import com.zyvioip.app.model.IpSignals
import com.zyvioip.app.model.IpType

/** Priority follows spec section 8: tor > proxy > vpn > datacenter > mobile > unknown. */
fun classifyIpType(signals: IpSignals, flagNames: Set<String> = signals.flags): IpType = when {
    signals.isTor == true || flagNames.contains(IpSignals.FLAG_TOR) -> IpType.TOR
    signals.isProxy == true || flagNames.contains(IpSignals.FLAG_PROXY) -> IpType.PROXY
    signals.isVpn == true || flagNames.contains(IpSignals.FLAG_VPN) -> IpType.VPN
    signals.isDatacenter == true || flagNames.contains(IpSignals.FLAG_DATACENTER) -> IpType.DATACENTER
    signals.isMobile == true || flagNames.contains(IpSignals.FLAG_MOBILE) -> IpType.MOBILE
    else -> IpType.UNKNOWN
}

/** Parse a comma-separated flag string such as "VPN,Proxy,Tor,Datacenter". */
fun parseFlags(raw: String?): Set<String> {
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
}
