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

    val settings: StateFlow<HomeSettings> = repository.homeSettings

    private val _refreshingIp = MutableStateFlow(false)
    /** 正在重新探测本机 IP（用于给刷新按钮转圈） */
    val refreshingIp: StateFlow<Boolean> = _refreshingIp.asStateFlow()

    private val _startupError = MutableStateFlow<String?>(null)
    val startupError: StateFlow<String?> = _startupError.asStateFlow()

    private val _configs = MutableStateFlow(emptyMap<String, ProviderConfig>())
    val configs: StateFlow<Map<String, ProviderConfig>> = _configs.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.initialize() }
                .onFailure { e ->
                    // 启动失败必须让用户看到原因，不能静默
                    if (e !is kotlinx.coroutines.CancellationException) {
                        _startupError.value = "初始化失败：${e.message ?: e.javaClass.simpleName}"
                    }
                }
            refreshConfigs()
        }
    }


    /**
     * 安全地在 viewModelScope 里执行挂起操作。
     *
     * 原来的 runCatching 会连 CancellationException 一起吞掉，
     * 这在协程里会破坏取消语义（外层已经取消，内层还以为正常结束），
     * 表现为状态没有写回、界面停在 LOADING。
     */
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _startupError.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun submit() {
        val value = _query.value.trim()
        // 输入框为空时直接用本机 IP，比点了没反应友好；两者都没有才忽略。
        val target = value.ifEmpty { repository.localIp.value.orEmpty() }
        if (target.isEmpty()) return
        launchSafely { repository.search(target) }
    }

    fun useLocalIp(ip: String) {
        _query.value = ip
        launchSafely { repository.search(ip) }
    }

    /** 手动刷新本机 IP；forceQuery=true 时即使 IP 未变也重查。 */
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

    /** 网络切换后调用：IP 变了才重查，避免无谓请求。 */
    fun onNetworkChanged() {
        launchSafely { repository.refreshLocalIp(forceQuery = false) }
    }

    fun retry(providerId: String) {
        launchSafely { repository.retry(providerId) }
    }

    fun addProvider(providerId: String) {
        launchSafely { repository.addProvider(providerId) }
    }

    fun removeProvider(providerId: String) {
        launchSafely { repository.removeProvider(providerId) }
    }

    fun saveConfig(providerId: String, config: ProviderConfig) {
        launchSafely { repository.setConfig(providerId, config) }
        viewModelScope.launch { refreshConfigs() }
    }

    fun updateSettings(next: HomeSettings) {
        launchSafely { repository.updateSettings(next) }
    }

    fun clearCache() {
        launchSafely { repository.clearCache() }
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
