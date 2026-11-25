package com.zyvioip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig

/** Global preferences plus credential management; no provider enable/disable switches here. */
@Composable
fun SettingsScreen(
    settings: HomeSettings,
    configs: Map<String, ProviderConfig>,
    onSettingsChange: (HomeSettings) -> Unit,
    onConfigChange: (String, ProviderConfig) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var reveal by remember { mutableStateOf(false) }
    var timeout by remember { mutableStateOf(settings.timeoutMs.toString()) }
    var ttl by remember { mutableStateOf(settings.cacheTtlMinutes.toString()) }
    LaunchedEffect(settings) {
        timeout = settings.timeoutMs.toString()
        ttl = settings.cacheTtlMinutes.toString()
    }
    val commit: (HomeSettings) -> Unit = { onSettingsChange(it) }
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("密钥只保存在本机，不会上传到除对应服务商以外的任何地方。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("供应商密钥", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("显示密钥", modifier = Modifier.weight(1f))
            Switch(checked = reveal, onCheckedChange = { reveal = it })
        }
        ProviderCatalog.all.filter { it.requiresKey || it.requiresEmail }.forEach { spec ->
            val config = configs[spec.id] ?: ProviderConfig(spec.id)
            if (spec.requiresKey) {
                var key by remember(spec.id) { mutableStateOf(config.apiKey) }
                LaunchedEffect(config.apiKey) { key = config.apiKey }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; onConfigChange(spec.id, config.copy(apiKey = key)) },
                    label = { Text("${spec.name} ${spec.configLabel.ifBlank { "API Key" }}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            if (spec.requiresEmail) {
                var email by remember(spec.id) { mutableStateOf(config.email) }
                LaunchedEffect(config.email) { email = config.email }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; onConfigChange(spec.id, config.copy(email = email)) },
                    label = { Text("GetIPIntel 联系邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        }

        Text("请求与缓存", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = timeout,
            onValueChange = { timeout = it; timeout.toLongOrNull()?.takeIf { v -> v > 0 }?.let { v -> commit(settings.copy(timeoutMs = v * 1000)) } },
            label = { Text("超时（秒）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ttl,
            onValueChange = { ttl = it; ttl.toLongOrNull()?.takeIf { v -> v >= 0 }?.let { v -> commit(settings.copy(cacheTtlMinutes = v)) } },
            label = { Text("缓存有效期（分钟，0 = 不缓存）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        SettingToggle("启动时自动查询本机 IP", settings.autoLookup) { commit(settings.copy(autoLookup = it)) }
        SettingToggle("显示风险标记", settings.showFlags) { commit(settings.copy(showFlags = it)) }
        SettingToggle("结果通知", settings.notifications) { commit(settings.copy(notifications = it)) }

        Button(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) { Text("清除缓存") }
        Text(
            "关于 ZyvioIP：本地聚合多家公开 IP 情报服务，仅用于网络安全自查，结果仅供参考。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
