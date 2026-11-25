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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = spec.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = statusText, color = statusColor, style = MaterialTheme.typography.labelMedium)
                if (status == ProviderStatus.ERROR || status == ProviderStatus.NEEDS_CONFIG) {
                    IconButton(onClick = { onRetry(spec.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重试 ${spec.name}")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                result?.purity?.let { purity ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("纯度 $purity") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = purityColor(purity),
                            disabledContainerColor = purityColor(purity).copy(alpha = 0.12f),
                        ),
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
                status == ProviderStatus.NEEDS_CONFIG -> "请在“添加供应商”中完成 ${spec.configLabel.ifBlank { "密钥" }} 配置"
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
    ProviderFlag.DATACENTER -> "数据中心"
    ProviderFlag.ABUSER -> "滥用"
    ProviderFlag.MOBILE -> "移动网络"
    else -> flag
}
