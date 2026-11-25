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
import com.zyvioip.app.ZyvioIpApp

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
    val error by viewModel.repository.error.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val configs by viewModel.configs.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf(false) }
    var configProviderId by remember { mutableStateOf<String?>(null) }

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
