package com.zyvioip.app.ui

import com.zyvioip.app.model.AggregatedResult
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderOutcome
import kotlinx.coroutines.flow.StateFlow

/** Contract implemented by the core lookup repository. */
interface HomeRepositoryApi {
    val providerStates: StateFlow<List<ProviderOutcome>>
    val aggregate: StateFlow<AggregatedResult?>
    val localIp: StateFlow<String?>
    val loading: StateFlow<Boolean>
    val error: StateFlow<String?>

    suspend fun initialize()
    suspend fun search(ip: String)
    suspend fun retry(providerId: String)
    suspend fun removeProvider(providerId: String)
    suspend fun addProvider(providerId: String)
    suspend fun setConfig(providerId: String, config: ProviderConfig)
    suspend fun getConfig(providerId: String): ProviderConfig
    suspend fun clearCache()
    suspend fun updateSettings(settings: HomeSettings)
}

data class HomeSettings(
    val timeoutMs: Long = 8_000,
    val cacheTtlMinutes: Long = 60,
    val autoLookup: Boolean = true,
    val showFlags: Boolean = true,
    val notifications: Boolean = false,
)

internal val AggregatedResult.countryCode: String? get() = signals.countryCode
internal val AggregatedResult.typeLabel: String get() = type.label
internal val AggregatedResult.flags: Set<String> get() = signals.flags
