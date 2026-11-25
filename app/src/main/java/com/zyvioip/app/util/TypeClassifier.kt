package com.zyvioip.app.util

import com.zyvioip.app.model.IpSignals
import com.zyvioip.app.model.IpType

/** Priority follows spec section 8: tor > proxy > vpn > datacenter > mobile > unknown. */
fun classifyIpType(signals: IpSignals, flagNames: Set<String> = signals.flags): IpType = when {
    // 优先级：Tor > VPN > 机房 > 代理 > 移动 > 住宅 > 未知
    // 注意机房要排在代理之前：像 8.8.8.8 这种 Google 机房 IP，
    // ip-api.com 会同时给 hosting=true 和 proxy=true，
    // 若代理优先就会把“机房”误判成“代理”，用户看到的类型就错了。
    signals.isTor == true || flagNames.contains(IpSignals.FLAG_TOR) -> IpType.TOR
    signals.isVpn == true || flagNames.contains(IpSignals.FLAG_VPN) -> IpType.VPN
    signals.isDatacenter == true || flagNames.contains(IpSignals.FLAG_DATACENTER) -> IpType.DATACENTER
    signals.isProxy == true || flagNames.contains(IpSignals.FLAG_PROXY) -> IpType.PROXY
    signals.isMobile == true || flagNames.contains(IpSignals.FLAG_MOBILE) -> IpType.MOBILE
    // 有源明确判定为住宅/原生 IP，且没命中上面的风险类型，才归到住宅
    signals.isResidential == true -> IpType.RESIDENTIAL
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
