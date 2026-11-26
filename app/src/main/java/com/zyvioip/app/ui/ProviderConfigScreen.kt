package com.zyvioip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.zyvioip.app.ui.theme.Spacing
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig

/** Configuration form for a single provider. All key fields are obscured by default. */
@Composable
fun ProviderConfigScreen(
    providerId: String,
    initial: ProviderConfig,
    onSave: (ProviderConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = ProviderCatalog.byId(providerId)
    var apiKey by remember(providerId) { mutableStateOf(initial.apiKey) }
    var email by remember(providerId) { mutableStateOf(initial.email) }
    var reveal by remember(providerId) { mutableStateOf(false) }

    LaunchedEffect(providerId, initial) {
        apiKey = initial.apiKey
        email = initial.email
    }

    Column(modifier = modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("配置 ${spec?.name ?: providerId}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (spec?.requiresKey == true) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(spec.configLabel.ifBlank { "API Key" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (reveal) VisualTransformation.None else SecretMask,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (reveal) KeyboardType.Text else KeyboardType.Password,
                ),
                trailingIcon = {
                    OutlinedButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示") }
                },
            )
        }
        if (spec?.requiresEmail == true) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(spec.configLabel.ifBlank { "联系邮箱" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        if (spec?.requiresKey != true && spec?.requiresEmail != true) {
            Text("该供应商无需配置，可直接添加。", style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { onSave(ProviderConfig(providerId, apiKey.trim(), email.trim())); onBack() },
            modifier = Modifier.fillMaxWidth(),
            enabled = (spec?.requiresKey != true || apiKey.isNotBlank()) && (spec?.requiresEmail != true || email.isNotBlank()),
        ) { Text("保存") }
    }
}
