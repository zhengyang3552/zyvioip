package com.zyvioip.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderOutcome
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    aggregate: com.zyvioip.app.model.AggregatedResult?,
    providerStates: List<ProviderOutcome>,
    localIp: String?,
    loading: Boolean,
    error: String?,
    settingsOpen: Boolean,
    pendingOpen: Boolean,
    configProviderId: String?,
    settings: HomeSettings,
    configs: Map<String, ProviderConfig>,
    onSettingsOpen: (Boolean) -> Unit,
    onPendingOpen: (Boolean) -> Unit,
    onConfigure: (String?) -> Unit,
    onSaveConfig: (String, ProviderConfig) -> Unit,
    onAddProvider: (String) -> Unit,
    onSettingsChange: (HomeSettings) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val activeIds = providerStates.map { it.providerId }.toSet()

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) snackbar.showSnackbar(error)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("ZyvioIP") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            configProviderId != null -> ProviderConfigScreen(
                providerId = configProviderId,
                initial = configs[configProviderId] ?: ProviderConfig(configProviderId),
                onSave = { onSaveConfig(configProviderId, it) },
                onBack = { onConfigure(null) },
                modifier = Modifier.padding(padding),
            )
            settingsOpen -> SettingsScreen(
                settings = settings,
                configs = configs,
                onSettingsChange = onSettingsChange,
                onConfigChange = onSaveConfig,
                onClearCache = onClearCache,
                onBack = { onSettingsOpen(false) },
                modifier = Modifier.padding(padding),
            )
            pendingOpen -> PendingProvidersScreen(
                activeIds = activeIds,
                results = providerStates.associateBy { it.providerId },
                onAdd = onAddProvider,
                onConfigure = { onConfigure(it) },
                onBack = { onPendingOpen(false) },
                modifier = Modifier.padding(padding),
            )
            else -> HomeContent(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = { keyboard?.hide(); onSubmit() },
                aggregate = aggregate,
                providerStates = providerStates,
                localIp = localIp,
                loading = loading,
                onSettingsOpen = { onSettingsOpen(true) },
                onPendingOpen = { onPendingOpen(true) },
                onRetry = onRetry,
                onRemove = { id ->
                    onRemove(id)
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            "已移除 ${ProviderCatalog.byId(id)?.name ?: id}",
                            actionLabel = "撤销",
                        )
                        if (result == SnackbarResult.ActionPerformed) onAddProvider(id)
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HomeContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    aggregate: com.zyvioip.app.model.AggregatedResult?,
    providerStates: List<ProviderOutcome>,
    localIp: String?,
    loading: Boolean,
    onSettingsOpen: () -> Unit,
    onPendingOpen: () -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("IP 地址") },
                placeholder = { Text(localIp ?: "输入 IPv4 / IPv6 地址") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
            IconButton(onClick = onSettingsOpen) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_manage),
                    contentDescription = "设置",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在查询…", style = MaterialTheme.typography.labelMedium)
            }
        }
        aggregate?.let { SummaryCard(it) }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(providerStates, key = { it.providerId }) { state ->
                ProviderDismissItem(state, onRetry = onRetry, onRemove = onRemove)
            }
        }
        Button(onClick = onPendingOpen, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text("添加供应商") }
    }
}

@Composable
private fun SummaryCard(result: com.zyvioip.app.model.AggregatedResult) {
    val color = purityColor(result.purity)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(countryFlagFromCode(result.countryCode), style = MaterialTheme.typography.headlineSmall)
                Text(result.ip, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = result.purity?.let { "$it" } ?: "--",
                    color = color,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            val place = listOfNotNull(result.signals.country, result.signals.city).joinToString(" · ").ifBlank { "位置未知" }
            Text(place, style = MaterialTheme.typography.bodyMedium)
            Text("类型：${result.typeLabel}", style = MaterialTheme.typography.labelMedium)
            if (result.flags.isNotEmpty()) {
                Text("标记：${result.flags.joinToString("、") { flagLabel(it) }}", style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

internal fun countryFlagFromCode(code: String?): String {
    if (code == null || code.length != 2) return "🌐"
    val upper = code.uppercase()
    if (!upper.all { it in 'A'..'Z' }) return "🌐"
    return buildString {
        append(Character.toChars(0x1F1E6 + (upper[0] - 'A')))
        append(Character.toChars(0x1F1E6 + (upper[1] - 'A')))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDismissItem(
    state: ProviderOutcome,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val spec = ProviderCatalog.byId(state.providerId) ?: return
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove(state.providerId)
                true
            } else false
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("删除", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        ProviderCard(spec = spec, result = state, onRetry = onRetry)
    }
}
