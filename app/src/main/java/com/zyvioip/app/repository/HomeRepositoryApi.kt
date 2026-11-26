package com.zyvioip.app.repository

import com.zyvioip.app.model.AggregatedResult
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderOutcome
import kotlinx.coroutines.flow.StateFlow

/**
 * 数据层对外合约。UI 层（View 系统）通过它读取状态、触发动作。
 *
 * 从原 ui 包迁到 repository 包：UI 层已改为 Activity + Adapter，
 * 这个合约属于数据层边界，不应依赖界面实现。
 */
interface HomeRepositoryApi {
    val providerStates: StateFlow<List<ProviderOutcome>>
    val aggregate: StateFlow<AggregatedResult?>
    val localIp: StateFlow<String?>
    val loading: StateFlow<Boolean>
    val error: StateFlow<String?>
    /** 持久化后的设置，UI 直接订阅 */
    val homeSettings: StateFlow<HomeSettings>

    suspend fun initialize()
    suspend fun search(ip: String)
    suspend fun retry(providerId: String)
    suspend fun removeProvider(providerId: String)
    suspend fun addProvider(providerId: String)
    suspend fun setConfig(providerId: String, config: ProviderConfig)
    suspend fun getConfig(providerId: String): ProviderConfig
    suspend fun clearCache()

    /**
     * 重新探测本机公网 IP。
     * @param forceQuery 为 true 时即使 IP 没变也重新查询
     * @return 检测到的新 IP（探测失败时为 null）
     */
    suspend fun refreshLocalIp(forceQuery: Boolean = false): String?
    suspend fun updateSettings(settings: HomeSettings)
}

data class HomeSettings(
    val timeoutMs: Long = 8_000,
    val cacheTtlMinutes: Long = 60,
    val autoLookup: Boolean = true,
    val showFlags: Boolean = true,
    val notifications: Boolean = false,
)

// 便捷访问器：AggregatedResult 的常用派生值，避免每处都写 signals.xxx
internal val AggregatedResult.countryCode: String? get() = signals.countryCode
internal val AggregatedResult.typeLabel: String get() = type.label
internal val AggregatedResult.flags: Set<String> get() = signals.flags
