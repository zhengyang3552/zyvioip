package com.zyvioip.app.provider

/** Result shape shared by the provider slice and the core repository. */
data class ProviderOutcome(
    val providerId: String,
    val status: ProviderStatus = com.zyvioip.app.provider.ProviderStatus.LOADING,
    val ip: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val city: String? = null,
    val region: String? = null,
    val company: String? = null,
    val asn: String? = null,
    val purity: Int? = null,
    /** 该源给出的原始风险分（0-100，越大越危险）；null = 该源不提供评分 */
    val riskScore: Int? = null,
    /** 是否原生/住宅 IP */
    val isResidential: Boolean? = null,
    /** 是否广播/保留地址 */
    val isBroadcast: Boolean? = null,
    val flags: Set<String> = emptySet(),
    val latencyMs: Long = 0,
    val message: String? = null,
    val error: String? = null,
    val fromCache: Boolean = false,
) {
    val succeeded: Boolean get() = status == ProviderStatus.SUCCESS
}
