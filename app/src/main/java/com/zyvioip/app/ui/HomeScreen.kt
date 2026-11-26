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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyvioip.app.ui.components.InfoPill
import com.zyvioip.app.ui.components.OutlinedPill
import com.zyvioip.app.ui.components.PillRow
import com.zyvioip.app.ui.theme.Spacing
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
    refreshingIp: Boolean = false,
    onRefreshLocalIp: () -> Unit = {},
    onUseLocalIp: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val activeIds = providerStates.map { it.providerId }.toSet()

    // 显式给 Short：不指定 duration 时，带 actionLabel 的 Snackbar 会一直等用户点击，
    // 表现就是“提示消不掉”。
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            snackbar.showSnackbar(message = error, duration = SnackbarDuration.Short)
        }
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
                refreshingIp = refreshingIp,
                onUseLocalIp = onUseLocalIp,
                onRefreshLocalIp = onRefreshLocalIp,
                onPendingOpen = { onPendingOpen(true) },
                onRetry = onRetry,
                onRemove = { id ->
                    onRemove(id)
                    scope.launch {
                        // 先清掉可能还在显示的旧提示，避免多条堆叠
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(
                            message = "已移除 ${ProviderCatalog.byId(id)?.name ?: id}",
                            actionLabel = "撤销",
                            duration = SnackbarDuration.Short,
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
    refreshingIp: Boolean,
    onUseLocalIp: (String) -> Unit,
    onRefreshLocalIp: () -> Unit,
    onPendingOpen: () -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // MD3 8dp 间距系统：屏幕左右统一 16dp 边距
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // MD3 搜索栏：形状为 full（胶囊），高度 56dp，内部 8dp 间距。
        // 之前用普通 OutlinedTextField + 独立 Button，形状与圆角都不符合规范。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.md),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("输入 IPv4 / IPv6", style = MaterialTheme.typography.bodyLarge) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                // MD3: search bar / 文本输入圆角为 full
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(18.dp))
                        }
                    }
                },
            )
            // MD3 filled button 高度 40dp，圆角 full
            Button(
                onClick = onSubmit,
                enabled = !loading,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            ) {
                Text("查询", style = MaterialTheme.typography.labelLarge)
            }
        }

        // 本机 IP：可点胶囊 + 刷新按钮
        localIp?.let { ip ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedPill(
                    text = "本机 IP $ip",
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onUseLocalIp(ip) },
                )
                if (refreshingIp) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefreshLocalIp, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新本机 IP",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("正在查询…", style = MaterialTheme.typography.labelMedium)
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
                // MD3 列表间距用 8dp 基准
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(bottom = Spacing.sm),
            ) {
                items(providerStates, key = { it.providerId }) { state ->
                    ProviderDismissItem(state, onRetry = onRetry, onRemove = onRemove)
                }
            }
        }

        // MD3 层级：主操作（查询）用 filled，次级操作（添加源）用 filled-tonal，
        // 避免与主按钮抢视觉重量。
        FilledTonalButton(
            onClick = onPendingOpen,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
            shape = CircleShape,
            contentPadding = PaddingValues(vertical = Spacing.md),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("添加供应商", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SummaryCard(result: com.zyvioip.app.model.AggregatedResult, showFlags: Boolean) {
    val riskColor = riskColor(result.riskScore)
    val succeeded = result.providerStates.count { it.outcome.succeeded }
    val failed = result.providerStates.count {
        it.outcome.status == com.zyvioip.app.provider.ProviderStatus.ERROR ||
            it.outcome.status == com.zyvioip.app.provider.ProviderStatus.NEEDS_CONFIG
    }
    val skipped = result.providerStates.count {
        it.outcome.status == com.zyvioip.app.provider.ProviderStatus.SKIPPED
    }

    // MD3: 摘要用 filled card（surfaceContainer 层级），圆角 medium(12)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 第一行：旗帜 + IP + 风险分（右对齐）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(countryFlagFromCode(result.signals.countryCode), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = result.ip,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                // 风险分用 display 级视觉重量，颜色映射风险档位
                Text(
                    text = result.riskScore?.toString() ?: "--",
                    color = riskColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "风险",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 第二行：地点 · 类型（次要信息，onSurfaceVariant）
            val place = listOfNotNull(result.signals.country, result.signals.city)
                .joinToString(" · ")
                .ifBlank { "位置未知" }
            Text(
                text = "$place · ${result.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 第三行：来源 + 标记 全部用胶囊展示
            PillRow {
                // 来源属性胶囊：带来源名，供追溯
                result.origin?.let { ov ->
                    InfoPill(
                        text = ov.origin.label,
                        emphasized = true,
                    )
                    OutlinedPill(text = ov.source)
                }
                // 风险标记胶囊
                if (showFlags) {
                    result.flags.forEach { flag ->
                        InfoPill(
                            text = flagLabel(flag),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // 来源说明放正文下方，避免和胶囊挤在一行
            result.origin?.let { ov ->
                Text(
                    text = ov.origin.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 各家风险分明细
            if (result.signals.riskScores.isNotEmpty()) {
                Text(
                    text = result.signals.riskScores.entries.joinToString("   ") { "${it.key} ${it.value}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val summaryLine = buildString {
                append("成功 $succeeded")
                if (failed > 0) append(" / 未成功 $failed")
                if (skipped > 0) append(" / 跳过 $skipped")
                if (result.scoredProviderCount > 0) append(" · ${result.scoredProviderCount} 个源参与评分")
            }
            Text(
                text = summaryLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (result.riskScore == null) {
                Text(
                    text = "没有源提供风险评分：免费接口多数只给归属信息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 风险分到颜色的映射：越低越绿，越高越红（MD3 语义色）。 */
@Composable
private fun riskColor(risk: Int?): Color = when {
    risk == null -> MaterialTheme.colorScheme.onSurfaceVariant
    risk >= 66 -> MaterialTheme.colorScheme.error
    risk >= 33 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
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
