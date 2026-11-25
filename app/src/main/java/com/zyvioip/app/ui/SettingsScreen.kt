package com.zyvioip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
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
        Text(
            "填好后点右侧“保存”，不会边输入边写盘。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("显示密钥", modifier = Modifier.weight(1f))
            Switch(checked = reveal, onCheckedChange = { reveal = it })
        }
        ProviderCatalog.all.filter { it.requiresKey || it.requiresEmail }.forEach { spec ->
            val config = configs[spec.id] ?: ProviderConfig(spec.id)
            // 原来 onValueChange 里直接 onConfigChange，等于每敲一个字母就写一次 DataStore：
            // IME 组合输入时会把半截字符也存进去，还会明显卡顿。改成编辑本地副本 + 显式保存。
            var key by remember(spec.id) { mutableStateOf(config.apiKey) }
            var email by remember(spec.id) { mutableStateOf(config.email) }
            LaunchedEffect(config.apiKey) { key = config.apiKey }
            LaunchedEffect(config.email) { email = config.email }
            val dirty = key != config.apiKey || email != config.email

            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (spec.requiresKey) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it },
                            label = { Text("${spec.name} ${spec.configLabel.ifBlank { "API Key" }}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        Button(
                            onClick = { onConfigChange(spec.id, ProviderConfig(spec.id, key.trim(), email.trim())) },
                            enabled = dirty,
                        ) { Text("保存") }
                    }
                }
                if (spec.requiresEmail) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("${spec.name} 联系邮箱") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                        Button(
                            onClick = { onConfigChange(spec.id, ProviderConfig(spec.id, key.trim(), email.trim())) },
                            enabled = dirty,
                        ) { Text("保存") }
                    }
                }
            }
        }

        Text("请求与缓存", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        // 同样的问题：原来 onValueChange 里直接 commit，删掉一位数字的瞬间（空字符串）不会写，
        // 但每敲一位都会触发一次 DataStore 写入并把焦点相关的重组全跑一遍。
        // 这里只在输入完成（失焦 / 提交）时落盘。
        OutlinedTextField(
            value = timeout,
            onValueChange = { timeout = it.filter { c -> c.isDigit() }.take(2) },
            label = { Text("超时（秒）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                timeout.toLongOrNull()?.takeIf { v -> v > 0 }?.let { v -> commit(settings.copy(timeoutMs = v * 1000)) }
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ttl,
            onValueChange = { ttl = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("缓存有效期（分钟，0 = 不缓存）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                ttl.toLongOrNull()?.takeIf { v -> v >= 0 }?.let { v -> commit(settings.copy(cacheTtlMinutes = v)) }
            }),
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
