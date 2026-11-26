package com.zyvioip.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.repository.HomeRepositoryApi
import com.zyvioip.app.repository.HomeSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 界面状态协调者。
 *
 * 只负责把 repository 的 StateFlow 暴露给 View 层，并转发用户动作。
 * 不做任何 UI 计算 —— 列表差异比对交给 DiffUtil。
 */
class MainViewModel(val repository: HomeRepositoryApi) : ViewModel() {

    private val _refreshingIp = MutableStateFlow(false)
    val refreshingIp: StateFlow<Boolean> = _refreshingIp.asStateFlow()

    private val _startupError = MutableStateFlow<String?>(null)
    val startupError: StateFlow<String?> = _startupError.asStateFlow()

    private val _configs = MutableStateFlow<Map<String, ProviderConfig>>(emptyMap())
    val configs: StateFlow<Map<String, ProviderConfig>> = _configs.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.initialize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _startupError.value = "初始化失败：${e.message ?: e.javaClass.simpleName}"
            }
            refreshConfigs()
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _startupError.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun search(ip: String) = launchSafely { repository.search(ip) }
    fun retry(id: String) = launchSafely { repository.retry(id) }
    fun addProvider(id: String) = launchSafely { repository.addProvider(id) }
    fun removeProvider(id: String) = launchSafely { repository.removeProvider(id) }
    fun clearCache() = launchSafely { repository.clearCache() }

    fun refreshLocalIp(forceQuery: Boolean = true) {
        if (_refreshingIp.value) return
        launchSafely {
            _refreshingIp.value = true
            try {
                repository.refreshLocalIp(forceQuery)
            } finally {
                _refreshingIp.value = false
            }
        }
    }

    /** 网络承载方式变化后调用：IP 变了才重查。 */
    fun onNetworkChanged() = launchSafely { repository.refreshLocalIp(forceQuery = false) }

    fun saveConfig(providerId: String, config: ProviderConfig) {
        launchSafely { repository.setConfig(providerId, config) }
        viewModelScope.launch { refreshConfigs() }
    }

    fun updateSettings(next: HomeSettings) = launchSafely { repository.updateSettings(next) }

    private suspend fun refreshConfigs() {
        _configs.value = com.zyvioip.app.provider.ProviderCatalog.all.associate { spec ->
            spec.id to runCatching { repository.getConfig(spec.id) }
                .getOrDefault(ProviderConfig(spec.id))
        }
    }

    class Factory(private val repository: HomeRepositoryApi) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
                "Unknown ViewModel: $modelClass"
            }
            return MainViewModel(repository) as T
        }
    }
}
