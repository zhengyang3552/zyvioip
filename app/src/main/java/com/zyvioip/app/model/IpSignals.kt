package com.zyvioip.app.model



/** Normalised location and abuse signals merged from every successful provider. */
data class IpSignals(
    val ip: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val city: String? = null,
    val region: String? = null,
    val company: String? = null,
    val asn: String? = null,
    val isVpn: Boolean? = null,
    val isProxy: Boolean? = null,
    val isTor: Boolean? = null,
    val isDatacenter: Boolean? = null,
    val isAbuser: Boolean? = null,
    val isMobile: Boolean? = null,
    /** 是否原生/住宅 IP（IPPure.isResidential）。null = 该源没给结论 */
    val isResidential: Boolean? = null,
    /** 是否广播/保留地址（IPPure.isBroadcast） */
    val isBroadcast: Boolean? = null,
    /** 各家给出的风险分（0-100，越大越危险），按来源名保存，用于展示明细 */
    val riskScores: Map<String, Int> = emptyMap(),
    /** 该源返回的原始字段（已拍平），用于卡片展开时展示全部明细 */
    val rawFields: Map<String, String> = emptyMap(),
    /** True when at least one provider explicitly reported a positive abuse signal. */
    val flags: Set<String> = emptySet(),
) {
    companion object {
        const val FLAG_VPN = "vpn"
        const val FLAG_PROXY = "proxy"
        const val FLAG_TOR = "tor"
        const val FLAG_DATACENTER = "datacenter"
        const val FLAG_ABUSER = "abuser"
        const val FLAG_MOBILE = "mobile"
        const val FLAG_RESIDENTIAL = "residential"
        const val FLAG_HOSTING = "hosting"
    }
}

/**
 * Merge non-null fields from `next` into `base`; null means "no opinion".
 * Booleans use OR semantics so a positive signal from one provider is never lost.
 */
fun mergeSignals(base: IpSignals, next: IpSignals): IpSignals = IpSignals(
    ip = next.ip ?: base.ip,
    country = next.country ?: base.country,
    countryCode = next.countryCode ?: base.countryCode,
    city = next.city ?: base.city,
    region = next.region ?: base.region,
    company = next.company ?: base.company,
    asn = next.asn ?: base.asn,
    isVpn = mergeBoolean(base.isVpn, next.isVpn),
    isProxy = mergeBoolean(base.isProxy, next.isProxy),
    isTor = mergeBoolean(base.isTor, next.isTor),
    isDatacenter = mergeBoolean(base.isDatacenter, next.isDatacenter),
    isAbuser = mergeBoolean(base.isAbuser, next.isAbuser),
    isMobile = mergeBoolean(base.isMobile, next.isMobile),
    isResidential = mergeBoolean(base.isResidential, next.isResidential),
    isBroadcast = mergeBoolean(base.isBroadcast, next.isBroadcast),
    riskScores = base.riskScores + next.riskScores,
    rawFields = base.rawFields + next.rawFields,
    flags = base.flags + next.flags,
)

private fun mergeBoolean(a: Boolean?, b: Boolean?): Boolean? = when {
    a == null -> b
    b == null -> a
    else -> a || b
}
