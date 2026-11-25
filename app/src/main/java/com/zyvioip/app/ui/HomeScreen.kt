package com.zyvioip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onUseLocalIp: (String) -> Unit = {},
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
        topBar = {
            TopAppBar(
                title = { Text("ZyvioIP") },
                navigationIcon = {
                    when {
                        configProviderId != null -> IconButton(onClick = { onConfigure(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                        settingsOpen -> IconButton(onClick = { onSettingsOpen(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                        pendingOpen -> IconButton(onClick = { onPendingOpen(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    // 设置入口只在首页显示；原来用的 android.R.drawable 系统图标在深色主题下几乎看不见。
                    if (configProviderId == null && !settingsOpen && !pendingOpen) {
                        IconButton(onClick = { onSettingsOpen(true) }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                },
            )
        },
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
                modifier = Modifier.padding(padding),
            )
            pendingOpen -> PendingProvidersScreen(
                activeIds = activeIds,
                configs = configs,
                onAdd = onAddProvider,
                onConfigure = { onConfigure(it) },
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
                showFlags = settings.showFlags,
                onUseLocalIp = onUseLocalIp,
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
    showFlags: Boolean,
    onUseLocalIp: (String) -> Unit,
    onPendingOpen: () -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 搜索区：原来用带浮动 label 的 OutlinedTextField（默认 56dp + label 撑高），
        // 再配一个大号 Button，比很多列表行都高。这里改成 dense 单行输入框 + 小按钮。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("输入 IPv4 / IPv6", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(16.dp))
                        }
                    }
                },
            )
            Button(
                onClick = onSubmit,
                enabled = !loading,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("查询", style = MaterialTheme.typography.labelLarge) }
        }

        // 本机公网 IP：做成一行紧凑的可点文字，不再占一整块胶囊高度。
        localIp?.let { ip ->
            Text(
                text = "本机 IP $ip · 点击查询",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onUseLocalIp(ip) }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            )
        }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("正在查询…", style = MaterialTheme.typography.labelSmall)
            }
        }

        aggregate?.let { SummaryCard(it, showFlags = showFlags) }

        if (providerStates.isNotEmpty()) {
            RemoveHint()
        }

        if (providerStates.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有启用任何供应商\n点下方按钮添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(providerStates, key = { it.providerId }) { state ->
                    ProviderDismissItem(state, onRetry = onRetry, onRemove = onRemove)
                }
            }
        }

        // 原来这里是个占满宽度的实心主按钮，视觉上比“查询”还重，喧宾夺主。
        // 改成弱化的描边按钮，并固定在底部安全区之上。
        TextButton(
            onClick = onPendingOpen,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加供应商", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SummaryCard(result: com.zyvioip.app.model.AggregatedResult, showFlags: Boolean) {
    val color = purityColor(result.purity)
    val succeeded = result.providerStates.count { it.outcome.succeeded }
    val failed = result.providerStates.count {
        it.outcome.status == com.zyvioip.app.provider.ProviderStatus.ERROR ||
            it.outcome.status == com.zyvioip.app.provider.ProviderStatus.NEEDS_CONFIG
    }
    // 紧凑版摘要：原来 IP 用 headlineSmall、分数用 headlineMedium，再加 16dp 内边距和 6dp 行距，
    // 一张卡能吃掉小屏近三分之一高度。这里整体降一档，并把“地点/类型”合到一行。
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(countryFlagFromCode(result.signals.countryCode), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = result.ip,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = result.purity?.toString() ?: "--",
                    color = color,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("纯净度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val place = listOfNotNull(result.signals.country, result.signals.city)
                .joinToString(" · ")
                .ifBlank { "位置未知" }
            Text(
                text = "$place · ${result.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                "成功 $succeeded" + if (failed > 0) " / 未成功 $failed" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (result.purity == null) {
                Text(
                    "暂无可评分结果：各来源均失败或未配置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (showFlags && result.flags.isNotEmpty()) {
                Text(
                    "标记：${result.flags.joinToString("、") { flagLabel(it) }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
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
