package com.zyvioip.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zyvioip.app.provider.ProviderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordinates UI state and delegates every lookup/cache operation to the core repository. */
class HomeViewModel(
    val repository: HomeRepositoryApi,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _settings = MutableStateFlow(HomeSettings())
    val settings: StateFlow<HomeSettings> = _settings.asStateFlow()

    private val _configs = MutableStateFlow(emptyMap<String, ProviderConfig>())
    val configs: StateFlow<Map<String, ProviderConfig>> = _configs.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.initialize() }
            refreshConfigs()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun submit() {
        val value = _query.value.trim()
        if (value.isEmpty()) return
        viewModelScope.launch { runCatching { repository.search(value) } }
    }

    fun useLocalIp(ip: String) {
        _query.value = ip
        viewModelScope.launch { runCatching { repository.search(ip) } }
    }

    fun retry(providerId: String) {
        viewModelScope.launch { runCatching { repository.retry(providerId) } }
    }

    fun addProvider(providerId: String) {
        viewModelScope.launch { runCatching { repository.addProvider(providerId) } }
    }

    fun removeProvider(providerId: String) {
        viewModelScope.launch { runCatching { repository.removeProvider(providerId) } }
    }

    fun saveConfig(providerId: String, config: ProviderConfig) {
        viewModelScope.launch {
            runCatching { repository.setConfig(providerId, config) }
            refreshConfigs()
        }
    }

    fun updateSettings(next: HomeSettings) {
        _settings.value = next
        viewModelScope.launch { runCatching { repository.updateSettings(next) } }
    }

    fun clearCache() {
        viewModelScope.launch { runCatching { repository.clearCache() } }
    }

    private suspend fun refreshConfigs() {
        val ids = com.zyvioip.app.provider.ProviderCatalog.all.map { it.id }
        _configs.value = ids.associateWith { id ->
            runCatching { repository.getConfig(id) }.getOrDefault(ProviderConfig(id))
        }
    }
}

class HomeViewModelFactory(
    private val repository: HomeRepositoryApi,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) { "Unknown ViewModel: $modelClass" }
        return HomeViewModel(repository) as T
    }
}
