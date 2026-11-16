package com.aikhata.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF126B56), onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EBDD), onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF49665A), background = Color(0xFFF5F8F5),
    surface = Color.White, surfaceVariant = Color(0xFFE4ECE6),
    error = Color(0xFFBA1A1A)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF83D5B7), onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF00513E), onPrimaryContainer = Color(0xFFA0F2D3),
    secondary = Color(0xFFB3CCBE), background = Color(0xFF101512),
    surface = Color(0xFF171E1A), surfaceVariant = Color(0xFF29352E),
    error = Color(0xFFFFB4AB)
)

@Composable
fun AiKhataTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
