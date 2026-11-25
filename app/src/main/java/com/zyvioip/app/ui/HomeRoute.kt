package com.zyvioip.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.zyvioip.app.ZyvioIpApp
import com.zyvioip.app.repository.NetworkWatcher

@Composable
fun HomeRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val repository = remember { (context as ZyvioIpApp).repository }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
    val query by viewModel.query.collectAsState()
    val aggregate by viewModel.repository.aggregate.collectAsState()
    val providerStates by viewModel.repository.providerStates.collectAsState()
    val localIp by viewModel.repository.localIp.collectAsState()
    val loading by viewModel.repository.loading.collectAsState()
    val repoError by viewModel.repository.error.collectAsState()
    val startupError by viewModel.startupError.collectAsState()
    // 启动异常优先展示，普通查询错误其次
    val error = startupError ?: repoError
    val settings by viewModel.settings.collectAsState()
    val configs by viewModel.configs.collectAsState()
    val refreshingIp by viewModel.refreshingIp.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf(false) }
    var configProviderId by remember { mutableStateOf<String?>(null) }

    // 网络切换（Wi-Fi <-> 移动网络）后自动重新探测本机 IP；
    // 只有 IP 真的变了才会重查，避免无谓请求。
    val appContext = LocalContext.current
    LaunchedEffect(appContext) {
        NetworkWatcher(appContext).changes().collect {
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
