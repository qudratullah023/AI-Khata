package com.aikhata.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import com.aikhata.app.ui.components.Eyebrow
import com.aikhata.app.ui.components.FuturisticBackdrop
import com.aikhata.app.ui.components.GlowPill
import com.aikhata.app.ui.components.NeonPanel
import com.aikhata.app.ui.components.ScreenEntrance

@Composable
fun SettingsScreen(
    shopName: String,
    biometricEnabled: Boolean,
    apiKeyCount: Int,
    appVersion: String,
    visualStyle: String,
    onVisualStyleChange: (String) -> Unit,
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

    FuturisticBackdrop {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 15.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                ScreenEntrance {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onBack) { Text("Back") }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Eyebrow("Control configuration")
                            Text("Settings", style = MaterialTheme.typography.headlineMedium)
                        }
                        GlowPill("PROTECTED")
                    }
                }
            }
            item {
                ScreenEntrance(delayMillis = 35) {
                    SettingsCard(
                        "App appearance",
                        "Choose how AI Khata looks. Your selection is applied immediately across the app and saved on this phone."
                    ) {
                        Text(
                            "VISUAL STYLE",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                        ThemeChoice(
                            title = "Liquid Glass",
                            detail = "Modern translucent layers and flowing colour.",
                            selected = visualStyle == "liquid",
                            onClick = { onVisualStyleChange("liquid") }
                        )
                        ThemeChoice(
                            title = "Glassmorphism",
                            detail = "Frosted-glass surfaces with soft depth.",
                            selected = visualStyle == "glass",
                            onClick = { onVisualStyleChange("glass") }
                        )
                        ThemeChoice(
                            title = "Soft UI · Neumorphism",
                            detail = "Gentle raised surfaces and soft shadows.",
                            selected = visualStyle == "soft",
                            onClick = { onVisualStyleChange("soft") }
                        )
                    }
                }
            }
            item {
                ScreenEntrance(delayMillis = 60) {
                    SettingsCard("Shop identity", "Your business name appears on the dashboard and shared statements.") {
                        OutlinedTextField(
                            value = shopNameValue,
                            onValueChange = { shopNameValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Shop name") }
                        )
                        Button(
                            onClick = { onSaveShopName(shopNameValue.trim()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save shop name") }
                    }
                }
            }
            item {
                ScreenEntrance(delayMillis = 110) {
                    SettingsCard("Access security", "Change your private shop PIN. Keep it somewhere safe.") {
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it.filter(Char::isDigit).take(12) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("New PIN") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { confirmPin = it.filter(Char::isDigit).take(12) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Button(
                            onClick = {
                                onChangePin(newPin, confirmPin)
                                newPin = ""
                                confirmPin = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = newPin.length >= 6 && newPin == confirmPin
                        ) { Text("Update PIN") }
                    }
                }
            }
            item {
                ScreenEntrance(delayMillis = 160) {
                    SettingsCard(
                        "Biometric access",
                        "Use your phone's fingerprint as a shortcut. Your shop PIN always remains available."
                    ) {
                        GlowPill(if (biometricEnabled) "FINGERPRINT ACTIVE" else "PIN SECURITY ACTIVE")
                        OutlinedButton(
                            onClick = { onToggleBiometric(!biometricEnabled) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (biometricEnabled) "Turn off fingerprint unlock" else "Enable fingerprint unlock")
                        }
                    }
                }
            }
            item {
                ScreenEntrance(delayMillis = 210) {
                    SettingsCard(
                        "AI vision keys",
                        "Personal Gemini keys are optional. The selected ledger photo is sent only when you start a scan."
                    ) {
                        GlowPill(if (apiKeyCount > 0) "$apiKeyCount ENCRYPTED KEY(S)" else "NO PERSONAL KEYS")
                        OutlinedTextField(
                            value = apiKeys,
                            onValueChange = { apiKeys = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Optional Gemini API keys") },
                            placeholder = { Text("Paste one key per line") }
                        )
                        Text(
                            "Saving new keys replaces the personal keys stored on this phone. Never share your API keys.",
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
                        ) { Text("Save keys securely") }
                        AnimatedVisibility(
                            visible = apiKeyCount > 0,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            OutlinedButton(onClick = onDeleteApiKeys, modifier = Modifier.fillMaxWidth()) {
                                Text("Remove all saved AI keys", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item {
                ScreenEntrance(delayMillis = 260) {
                    SettingsCard(
                        "Data vault",
                        "Move your encrypted shop data between phones using Google Drive or any file provider. Choose Google Drive in the Android save/open screen. Sign in to the same Google account on both phones and use the same backup password. This is a secure manual backup/restore, not automatic live sync."
                    ) {
                        Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                            Text("Save encrypted backup · Google Drive / phone")
                        }
                        OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) {
                            Text("Restore · Google Drive / phone")
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(3.dp))
                Text(
                    "AI KHATA  •  VERSION $appVersion  •  LOCAL-FIRST",
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    letterSpacing = 1.1.sp
                )
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        border = BorderStroke(
            if (selected) 1.6.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    detail,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            Text(
                if (selected) "✓ SELECTED" else "CHOOSE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    detail: String,
    content: @Composable () -> Unit
) {
    NeonPanel(Modifier.fillMaxWidth(), radius = 21.dp) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("System preference")
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            }
            content()
        }
    }
}
