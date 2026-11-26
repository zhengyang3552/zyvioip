package com.zyvioip.app.ui

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.zyvioip.app.ZyvioIpApp
import com.zyvioip.app.repository.NetworkWatcher

@Composable
fun HomeRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val repository = remember { (context as ZyvioIpApp).repository }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
    val query by viewModel.query.collectAsStateWithLifecycle()
    val aggregate by viewModel.repository.aggregate.collectAsStateWithLifecycle()
    val providerStates by viewModel.repository.providerStates.collectAsStateWithLifecycle()
    val localIp by viewModel.repository.localIp.collectAsStateWithLifecycle()
    val loading by viewModel.repository.loading.collectAsStateWithLifecycle()
    val repoError by viewModel.repository.error.collectAsStateWithLifecycle()
    val startupError by viewModel.startupError.collectAsStateWithLifecycle()
    // 启动异常优先展示，普通查询错误其次
    val error = startupError ?: repoError
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val refreshingIp by viewModel.refreshingIp.collectAsStateWithLifecycle()
    // 用 rememberSaveable：转屏或进程被回收后能恢复到原页面，
    // 否则从设置页返回会莫名回到首页。
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var pendingOpen by rememberSaveable { mutableStateOf(false) }
    var configProviderId by rememberSaveable { mutableStateOf<String?>(null) }

    // 网络切换（Wi-Fi <-> 移动网络）后自动重新探测本机 IP。
    //
    // 注意：必须去重。onCapabilitiesChanged 在网络参数微调时会反复回调，
    // 若每次都触发一次全量查询，用户会明显感到“隔一阵卡一下”。
    // NetworkWatcher 内部已做 debounce，这里再用 distinctUntilChanged 兜一层。
    val appContext = LocalContext.current
    LaunchedEffect(appContext) {
        NetworkWatcher(appContext).changes()
            .distinctUntilChanged()
            .collect {
                viewModel.onNetworkChanged()
            }
    }

    // System back should navigate back one settings level, never finish the Activity from a subpage.
    BackHandler(enabled = configProviderId != null) { configProviderId = null }
    BackHandler(enabled = configProviderId == null && pendingOpen) { pendingOpen = false }
    BackHandler(enabled = configProviderId == null && !pendingOpen && settingsOpen) { settingsOpen = false }

    HomeScreen(
        query = query,
        onQueryChange = viewModel::setQuery,
        onSubmit = { viewModel.submit() },
        aggregate = aggregate,
        providerStates = providerStates,
        localIp = localIp,
        loading = loading,
        error = error,
        settingsOpen = settingsOpen,
        pendingOpen = pendingOpen,
        configProviderId = configProviderId,
        settings = settings,
        configs = configs,
        onSettingsOpen = { settingsOpen = it },
        onPendingOpen = { pendingOpen = it },
        onConfigure = { configProviderId = it },
        onSaveConfig = viewModel::saveConfig,
        onAddProvider = viewModel::addProvider,
        onSettingsChange = viewModel::updateSettings,
        onRetry = viewModel::retry,
        onRemove = viewModel::removeProvider,
        onClearCache = viewModel::clearCache,
        refreshingIp = refreshingIp,
        onRefreshLocalIp = { viewModel.refreshLocalIp(forceQuery = true) },
        onUseLocalIp = viewModel::useLocalIp,
        modifier = modifier,
    )
}

/** Small local Scaffold wrapper kept for previews and host screens. */
@Composable
fun HomeScreenHost(content: @Composable () -> Unit) {
    androidx.compose.material3.Scaffold { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { content() }
    }
}
