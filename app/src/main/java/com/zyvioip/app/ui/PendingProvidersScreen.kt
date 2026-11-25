package com.zyvioip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderOutcome
import com.zyvioip.app.provider.ProviderSpec
import com.zyvioip.app.provider.ProviderStatus

/** Lists all providers that are not currently active, with their configuration state. */
@Composable
fun PendingProvidersScreen(
    activeIds: Set<String>,
    results: Map<String, ProviderOutcome>,
    onAdd: (String) -> Unit,
    onConfigure: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending: List<ProviderSpec> = ProviderCatalog.all.filterNot { it.id in activeIds }
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("添加供应商", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("选择并配置后即可加入查询。密钥只保存在本机。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pending, key = { it.id }) { spec ->
                val needsConfig = results[spec.id]?.status == ProviderStatus.NEEDS_CONFIG || spec.requiresKey || spec.requiresEmail
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(spec.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(spec.note.ifBlank { "无需配置" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (needsConfig) "配置状态：需要配置" else "配置状态：可直接添加",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (needsConfig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (needsConfig) OutlinedButton(onClick = { onConfigure(spec.id) }) { Text("配置") }
                            Button(onClick = { onAdd(spec.id) }) { Text("添加") }
                        }
                    }
                }
            }
        }
    }
}
