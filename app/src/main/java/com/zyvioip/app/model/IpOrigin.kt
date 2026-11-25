package com.zyvioip.app.model

/**
 * IP「来源属性」——说明这个地址属于哪类网络，而不是“危险程度”。
 *
 * 与 [IpType]（风险类型：VPN/代理/Tor…）是**两个维度**：
 * 一个 IP 可以既是「机房」又是「广播地址」。
 *
 * 各家的判定依据并不统一，所以这里保留 [source] 字段，
 * 明确告诉用户“这个结论是哪个源给的”，避免把某家的判断当成事实。
 */
enum class IpOrigin(val label: String, val hint: String) {
    /** 住宅宽带，通常是最“干净”的普通用户线路 */
    RESIDENTIAL("住宅宽带", "运营商分配给家庭用户的线路"),

    /** 原生 IP：IP 注册地与机房/服务所在地一致，未被二次广播 */
    NATIVE("原生 IP", "IP 注册归属与实际落地一致"),

    /** 广播 IP：通过 BGP anycast/转租等方式在其他地区宣告，注册地与实际不符 */
    BROADCAST("广播 IP", "IP 在其他地区被转播，注册地可能与实际不符"),

    /** 数据中心 / 机房 */
    DATACENTER("机房 / IDC", "数据中心、云厂商或 IDC 机房的地址段"),

    /** 企业专线 / 商业线路 */
    BUSINESS("企业专线", "企业或商业宽带，非家庭线路"),

    /** 移动网络 */
    MOBILE("移动网络", "蜂窝网络出口（4G/5G 共享地址）"),

    UNKNOWN("未知", "现有来源都没有给出明确结论"),
}

/** 一个来源对某 IP 的“来源属性”判定。 */
data class OriginVerdict(
    val origin: IpOrigin,
    /** 给出这个结论的来源名，例如 "IPPure"、"ip-api.com" */
    val source: String,
)

/**
 * 汇总各来源的判定结果。
 *
 * 优先级：广播 > 机房 > 住宅/原生 > 企业 > 移动 > 未知
 * —— 广播和机房是更强的“硬结论”，不应该被普通的地理信息覆盖掉。
 */
fun resolveOrigin(verdicts: List<OriginVerdict>): OriginVerdict? {
    if (verdicts.isEmpty()) return null
    val priority = listOf(
        IpOrigin.BROADCAST,
        IpOrigin.DATACENTER,
        IpOrigin.NATIVE,
        IpOrigin.RESIDENTIAL,
        IpOrigin.BUSINESS,
        IpOrigin.MOBILE,
    )
    return priority.firstNotNullOfOrNull { want -> verdicts.firstOrNull { it.origin == want } }
}
