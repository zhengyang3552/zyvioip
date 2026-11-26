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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
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
                onAdd = { id ->
                    onAddProvider(id)
                    // 添加后直接回首页，让用户马上看到新卡片；
                    // 留在添加页会让人以为没生效而反复点击。
                    onPendingOpen(false)
                },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // 整页统一一个 LazyColumn：
    // 之前搜索栏/摘要卡固定在顶部，只让供应商列表滚，导致小屏上摘要卡吃掉半屏，
    // 底下能看到的供应商只剩一两个。现在所有内容一起滚，滑动方向单一。
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.md,
            bottom = Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // ---- 搜索栏 ----
        // 抽成独立 composable：query 每敲一个字都会变，留在 HomeContent 里
        // 会让整个 LazyColumn 的 content lambda 重建（所有卡片跟着重组）。
        item(key = "search") {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                loading = loading,
            )
        }

        // ---- 本机 IP ----
        localIp?.let { ip ->
            item(key = "local-ip") {
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
                        IconButton(onClick = onRefreshLocalIp, modifier = Modifier.size(48.dp)) {
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
        }

        // ---- 查询中 ----
        if (loading) {
            item(key = "loading") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("正在查询…", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ---- 摘要卡 ----
        aggregate?.let { result ->
            item(key = "summary") { SummaryCard(result, showFlags = showFlags) }
        }

        // ---- 提示 ----
        if (providerStates.isNotEmpty()) {
            item(key = "hint") { RemoveHint() }
        }

        // ---- 空状态 ----
        if (providerStates.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "还没有启用任何供应商\n点下方按钮添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ---- 供应商卡片 ----
        items(providerStates, key = { it.providerId }) { state ->
            ProviderDismissItem(
                state = state,
                onRetry = onRetry,
                onRemove = onRemove,
                modifier = Modifier
                    // 增删时让相邻卡片平滑让位，而不是瞬间跳变
                    .animateItemPlacement(),
            )
        }

        // ---- 底部按钮 ----
        item(key = "add-provider") {
            FilledTonalButton(
                onClick = onPendingOpen,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                shape = CircleShape,
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("添加供应商", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 搜索栏。独立成一个 composable，让 query 的高频变化只重组这里。
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    loading: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("输入 IPv4 / IPv6", style = MaterialTheme.typography.bodyLarge) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
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
        // 圆形图标按钮（56dp），与输入框同高
        FilledIconButton(
            onClick = onSubmit,
            enabled = !loading,
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "查询",
                    modifier = Modifier.size(24.dp),
                )
            }
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
                // 风险分：数字与「风险」二字底部对齐。
                // 之前一行里混用 headlineSmall 和 labelSmall，两者行高差很大，
                // 基线对不齐，截图里数字明显偏高。
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
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
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
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
                // 风险标记胶囊：用描边样式而不是实心红底。
                // 截图里 5 个标记全是深红块，和风险分数字互相抢注意力；
                // 描边更克制，也符合 MD3「容器色表达层级」的用法。
                if (showFlags) {
                    result.flags.forEach { flag ->
                        OutlinedPill(
                            text = flagLabel(flag),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
