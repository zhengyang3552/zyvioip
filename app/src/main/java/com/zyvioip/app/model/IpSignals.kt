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
    flags = base.flags + next.flags,
)

private fun mergeBoolean(a: Boolean?, b: Boolean?): Boolean? = when {
    a == null -> b
    b == null -> a
    else -> a || b
}
