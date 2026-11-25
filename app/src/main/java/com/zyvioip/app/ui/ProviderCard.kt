package com.zyvioip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        ProviderStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = spec.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = statusText, color = statusColor, style = MaterialTheme.typography.labelMedium)
                // 重试按钮始终显示，只在“查询中”时禁用。
                // 之前只在 ERROR / NEEDS_CONFIG 时出现，用户成功后想手动刷新却找不到入口；
                // 而且新增的 SKIPPED 状态也会让它消失。
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 显示“风险 N”而不是“纯度 N”：各家接口给的本来就是风险/欺诈分，
                // 直接展示原始语义，避免用户反向理解。
                result?.riskScore?.let { risk ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("风险 $risk") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = purityColor(100 - risk),
                            disabledContainerColor = purityColor(100 - risk).copy(alpha = 0.12f),
                        ),
                    )
                }
                // 优先显示更具体的来源判定（原生/广播/机房），其次是粗略的住宅标记
                result?.origin?.let { origin ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(origin.label, style = MaterialTheme.typography.labelSmall) },
                    )
                } ?: result?.isResidential?.let { residential ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(if (residential) "住宅/原生" else "非住宅", style = MaterialTheme.typography.labelSmall) },
                    )
                }
                result?.flags?.forEach { flag ->
                    AssistChip(onClick = {}, enabled = false, label = { Text(flagLabel(flag), style = MaterialTheme.typography.labelSmall) })
                }
                result?.latencyMs?.takeIf { it > 0 }?.let {
                    Text(text = "${it} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val detail = when {
                // 这个供应商已经在列表里了，不该让用户去“添加供应商”里找它。
                status == ProviderStatus.NEEDS_CONFIG -> "尚未配置 ${spec.configLabel.ifBlank { "密钥" }}，点击右上角重试或到设置中填写"
                status == ProviderStatus.ERROR -> result?.error ?: "查询失败"
                status == ProviderStatus.SUCCESS -> listOfNotNull(
                    result?.country, result?.city, result?.company, result?.asn,
                ).distinct().joinToString(" · ").ifBlank { result?.message }.orEmpty()
                else -> spec.note
            }
            if (detail.isNotBlank()) {
                Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
