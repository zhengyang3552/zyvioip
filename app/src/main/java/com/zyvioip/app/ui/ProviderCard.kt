package com.zyvioip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.zyvioip.app.ui.components.InfoPill
import com.zyvioip.app.ui.components.OutlinedPill
import com.zyvioip.app.ui.components.PillRow
import com.zyvioip.app.ui.theme.Spacing
import com.zyvioip.app.provider.ProviderFlag
import com.zyvioip.app.provider.ProviderOutcome
import com.zyvioip.app.provider.ProviderSpec
import com.zyvioip.app.provider.ProviderStatus

/** Colour shared by the main purity card and per-provider purity chips. */
fun purityColor(purity: Int?): Color = when {
    purity == null -> Color(0xFF8A8F98)
    purity >= 70 -> Color(0xFF2E9E5B)
    purity >= 40 -> Color(0xFFD08A1E)
    else -> Color(0xFFD64B4B)
}

@Composable
fun ProviderCard(
    spec: ProviderSpec,
    result: ProviderOutcome?,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 展开状态由外层持有：左滑手势需要知道当前是否展开，以决定是否拦截触摸 */
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    val status = result?.status ?: ProviderStatus.PENDING
    val statusText = when (status) {
        ProviderStatus.PENDING -> "等待中"
        ProviderStatus.SKIPPED -> "已跳过"
        ProviderStatus.LOADING -> "查询中"
        ProviderStatus.SUCCESS -> "成功"
        ProviderStatus.ERROR -> "失败"
        ProviderStatus.NEEDS_CONFIG -> "需要配置"
    }
    val statusColor = when (status) {
        ProviderStatus.SUCCESS -> Color(0xFF2E9E5B)
        ProviderStatus.ERROR -> Color(0xFFD64B4B)
        ProviderStatus.NEEDS_CONFIG -> Color(0xFFD08A1E)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val raw = result?.rawFields.orEmpty()
    val hasDetail = raw.isNotEmpty()

    // MD3: card 圆角 medium(12)，容器用 surfaceContainerLow 体现层级，
    // 深度靠色调而非阴影（tonal surfaces replace elevation shadows）
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(text = statusText, color = statusColor)
                // 重试按钮始终显示，只在“查询中”时禁用
                IconButton(
                    onClick = { onRetry(spec.id) },
                    enabled = status != ProviderStatus.LOADING,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = if (status == ProviderStatus.LOADING) "查询中" else "重试 ${spec.name}",
                        modifier = Modifier.size(16.dp),
                        tint = if (status == ProviderStatus.LOADING) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                // 折叠/展开：只有拿到明细时才显示箭头
                if (hasDetail) {
                    IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "收起 ${spec.name}" else "展开 ${spec.name} 的全部字段",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 简要徽标：风险分 / 来源属性 / 标记
            // 风险分 / 来源 / 标记：统一用胶囊展示（MD3 chip，圆角 full）
            PillRow {
                result?.riskScore?.let { risk ->
                    InfoPill(
                        text = "风险 $risk",
                        containerColor = purityColor(100 - risk).copy(alpha = 0.14f),
                        contentColor = purityColor(100 - risk),
                    )
                }
                result?.origin?.let { origin ->
                    InfoPill(text = origin.label, emphasized = false)
                }
                result?.flags?.forEach { flag ->
                    InfoPill(
                        text = flagLabel(flag),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                result?.latencyMs?.takeIf { it > 0 && it < 120_000 }?.let {
                    OutlinedPill(text = "${it} ms")
                }
            }

            // 折叠时的摘要行
            if (!expanded) {
                val detail = when {
                    status == ProviderStatus.NEEDS_CONFIG ->
                        "尚未配置 ${spec.configLabel.ifBlank { "密钥" }}，点击右上角重试或到设置中填写"
                    status == ProviderStatus.ERROR -> result?.error ?: "查询失败"
                    status == ProviderStatus.SUCCESS -> listOfNotNull(
                        result?.country, result?.city, result?.company, result?.asn,
                    ).distinct().joinToString(" · ").ifBlank { result?.message }.orEmpty()
                    else -> spec.note
                }
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 展开：完整字段表
            if (expanded && hasDetail) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = Spacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = "${spec.name} 返回的全部字段（${raw.size} 项）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                // 字段较多，用纵向滚动避免撑爆卡片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    raw.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1.2f),
                            )
                        }
                    }
                }
                result?.message?.takeIf { it.isNotBlank() && status == ProviderStatus.SUCCESS }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun flagLabel(flag: String): String = when (flag) {
    ProviderFlag.VPN -> "VPN"
    ProviderFlag.PROXY -> "代理"
    ProviderFlag.TOR -> "Tor"
    ProviderFlag.DATACENTER -> "机房"
    ProviderFlag.ABUSER -> "滥用"
    ProviderFlag.MOBILE -> "移动网络"
    else -> flag
}

/**
 * 状态胶囊：把「成功 / 失败 / 查询中」做成小圆角标签。
 *
 * MD3 中状态属于离散元信息，用 chip 语义（胶囊形）比普通文本更易扫读。
 * 颜色取自语义色，保证深浅主题下对比度一致。
 */
@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 2.dp),
        )
    }
}
