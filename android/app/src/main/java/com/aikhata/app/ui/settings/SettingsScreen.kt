package com.aikhata.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    shopName: String,
    biometricEnabled: Boolean,
    apiKeyCount: Int,
    appVersion: String,
    onBack: () -> Unit,
    onSaveShopName: (String) -> Unit,
    onChangePin: (String, String) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onSaveApiKeys: (String) -> Unit,
    onDeleteApiKeys: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var shopNameValue by remember(shopName) { mutableStateOf(shopName) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var apiKeys by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Manage your shop, security, AI photo reading and backups.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        item {
            SettingsCard(title = "Shop details", detail = "This name appears on your dashboard and shared statements.") {
                OutlinedTextField(
                    value = shopNameValue,
                    onValueChange = { shopNameValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Shop name") }
                )
                Button(
                    onClick = { onSaveShopName(shopNameValue.trim()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save shop name") }
            }
        }
        item {
            SettingsCard(title = "Change shop PIN", detail = "Your PIN must contain at least six digits.") {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("New PIN") },
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Confirm new PIN") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = {
                        onChangePin(newPin, confirmPin)
                        newPin = ""
                        confirmPin = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Update PIN") }
            }
        }
        item {
            SettingsCard(
                title = "Fingerprint unlock",
                detail = "Use a registered fingerprint as an optional shortcut. Your shop PIN remains available."
            ) {
                OutlinedButton(
                    onClick = { onToggleBiometric(!biometricEnabled) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (biometricEnabled) "Turn off fingerprint unlock" else "Enable fingerprint unlock")
                }
                Text(
                    if (biometricEnabled) "Fingerprint unlock is currently enabled." else "Fingerprint unlock is currently off.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        item {
            SettingsCard(
                title = "AI photo reading",
                detail = "The selected ledger photo is sent to the configured AI service only when you start a scan. API keys are encrypted on this phone."
            ) {
                Text(
                    if (apiKeyCount > 0) apiKeyCount.toString() + " personal Gemini key(s) saved securely." else "No personal Gemini keys saved. The built-in service is tried first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = apiKeys,
                    onValueChange = { apiKeys = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("Optional Gemini API keys") },
                    placeholder = { Text("Paste one key per line") }
                )
                Text(
                    "Pasting new keys replaces the currently saved personal keys. Never share your keys with anyone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Button(
                    onClick = {
                        onSaveApiKeys(apiKeys)
                        apiKeys = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apiKeys.isNotBlank()
                ) { Text("Save AI keys securely") }
                if (apiKeyCount > 0) {
                    OutlinedButton(onClick = onDeleteApiKeys, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove all saved AI keys", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            SettingsCard(
                title = "Backup and restore",
                detail = "Backups include your shop name, customers and transactions. They do not include your PIN or AI keys."
            ) {
                Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                    Text("Export backup")
                }
                OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore from backup")
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("AI Khata  ·  Version " + appVersion, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    detail: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            content()
        }
    }
}
