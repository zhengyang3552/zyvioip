package com.zyvioip.app.model

import androidx.compose.runtime.Immutable
import com.zyvioip.app.provider.ProviderOutcome

/** A provider spec paired with the provider slice's current outcome. */
@Immutable
data class ProviderState(
    val spec: ProviderSpec,
    val outcome: ProviderOutcome,
)

/** Combined view returned by the lookup repository. */
@Immutable
data class AggregatedResult(
    val ip: String,
    val signals: IpSignals = IpSignals(ip = ip),
    val providerStates: List<ProviderState> = emptyList(),
    /** 纯净度 0-100（风险分的反面）。null = 没有任何源提供风险分。 */
    val purity: Int? = null,
    /**
     * 平均风险分 0-100（越大越危险）。
     * 与 purity 同时存在，是为了让用户直接看到“风险 28”而不是反推 72。
     */
    val riskScore: Int? = null,
    /** 有多少个源真正参与了评分，UI 用来说明“这个分数由几个源算出” */
    val scoredProviderCount: Int = 0,
    val type: IpType = IpType.UNKNOWN,
    /** IP 来源属性（住宅/原生/广播/机房…）及给出结论的来源 */
    val origin: OriginVerdict? = null,
    /** 所有源给出的来源判定明细，UI 可展示“谁说了什么” */
    val originVerdicts: List<OriginVerdict> = emptyList(),
)

// 注意：不要往 AggregatedResult 里加 completedAtMillis 之类每次都变的字段。
// StateFlow 的去重依赖 data class 的 equals —— 带时间戳会让每次 rebuild
// 都判定为「内容变了」，SummaryCard 与整个列表必然重组。

enum class IpType(val label: String) {
    VPN("VPN"),
    PROXY("代理"),
    TOR("Tor"),
    DATACENTER("机房/数据中心"),
    MOBILE("移动网络"),
    RESIDENTIAL("住宅/原生 IP"),
    UNKNOWN("未知"),
}
