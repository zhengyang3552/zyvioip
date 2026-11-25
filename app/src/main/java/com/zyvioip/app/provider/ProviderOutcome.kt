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
    /** 该源对“IP 来源属性”的判定（住宅/原生/广播/机房…），null = 该源没有能力判断 */
    val origin: com.zyvioip.app.model.IpOrigin? = null,
    val flags: Set<String> = emptySet(),
    val latencyMs: Long = 0,
    val message: String? = null,
    val error: String? = null,
    val fromCache: Boolean = false,
    /**
     * 该源返回的**全部原始字段**（已把嵌套对象拍平）。
     * 用途：卡片展开时展示完整数据，避免请求了却只显示几个字段浪费掉。
     * 只保留有实际值的键，嵌套结构用 "a.b" 形式表示。
     */
    val rawFields: Map<String, String> = emptyMap(),
) {
    val succeeded: Boolean get() = status == ProviderStatus.SUCCESS
}
