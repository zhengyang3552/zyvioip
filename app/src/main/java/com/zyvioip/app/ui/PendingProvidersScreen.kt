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
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderSpec

/** Lists all providers that are not currently active, with their configuration state. */
@Composable
fun PendingProvidersScreen(
    activeIds: Set<String>,
    configs: Map<String, ProviderConfig>,
    onAdd: (String) -> Unit,
    onConfigure: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending: List<ProviderSpec> = ProviderCatalog.all.filterNot { it.id in activeIds }
    // 真实判断“还缺配置”：看本地已保存的 key/email，而不是看 spec 是否要求 key。
    // 旧逻辑只要 spec.requiresKey 就是红字“需要配置”，即使已经填过密钥，用户会以为配置没生效。
    fun isConfigured(spec: ProviderSpec, config: ProviderConfig?): Boolean {
        val c = config ?: ProviderConfig(spec.id)
        val keyOk = !spec.requiresKey || c.apiKey.isNotBlank()
        val emailOk = !spec.requiresEmail || c.email.isNotBlank()
        return keyOk && emailOk
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("添加供应商", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("选择并配置后即可加入查询。密钥只保存在本机。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (pending.isEmpty()) {
            Text("所有供应商都已添加。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pending, key = { it.id }) { spec ->
                val config = configs[spec.id]
                val configured = isConfigured(spec, config)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(spec.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(spec.note.ifBlank { "无需配置" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (configured) "配置状态：可直接添加" else "配置状态：需要配置",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (spec.requiresKey || spec.requiresEmail) {
                                OutlinedButton(onClick = { onConfigure(spec.id) }) {
                                    Text(if (configured) "修改配置" else "配置")
                                }
                            }
                            Button(onClick = { onAdd(spec.id) }, enabled = configured) { Text("添加") }
                        }
                    }
                }
            }
        }
    }
}
