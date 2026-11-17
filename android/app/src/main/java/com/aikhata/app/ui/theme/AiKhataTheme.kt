package com.aikhata.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SoftLight = lightColorScheme(
    primary = Color(0xFF526E91), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7F5), onPrimaryContainer = Color(0xFF172C45),
    secondary = Color(0xFF8A78A8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE2F3), onSecondaryContainer = Color(0xFF332746),
    tertiary = Color(0xFF9A7656), background = Color(0xFFE9EEF5), onBackground = Color(0xFF263447),
    surface = Color(0xFFF0F4F9), onSurface = Color(0xFF263447),
    surfaceVariant = Color(0xFFE1E8F1), onSurfaceVariant = Color(0xFF627187),
    outline = Color(0xFFB7C4D4), outlineVariant = Color(0xFFD4DDE8), error = Color(0xFFB94E62)
)
private val SoftDark = darkColorScheme(
    primary = Color(0xFFB5CBE8), onPrimary = Color(0xFF263A53),
    primaryContainer = Color(0xFF405773), onPrimaryContainer = Color(0xFFE0ECFA),
    secondary = Color(0xFFD1BCEB), onSecondary = Color(0xFF392B4B),
    secondaryContainer = Color(0xFF514264), onSecondaryContainer = Color(0xFFF0E4FF),
    tertiary = Color(0xFFE5C29E), background = Color(0xFF202A38), onBackground = Color(0xFFE8EFF8),
    surface = Color(0xFF293545), onSurface = Color(0xFFE8EFF8),
    surfaceVariant = Color(0xFF354456), onSurfaceVariant = Color(0xFFB9C7D8),
    outline = Color(0xFF63768D), outlineVariant = Color(0xFF3D4B5E), error = Color(0xFFFFA1AD)
)
private val GlassLight = lightColorScheme(
    primary = Color(0xFF087F8C), onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F1F3), onPrimaryContainer = Color(0xFF00363B),
    secondary = Color(0xFF5B65B8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E4FF), onSecondaryContainer = Color(0xFF171E55),
    tertiary = Color(0xFFB06B9A), background = Color(0xFFEAF5F8), onBackground = Color(0xFF102B38),
    surface = Color(0xFFF7FCFD), onSurface = Color(0xFF102B38),
    surfaceVariant = Color(0xFFDCECF0), onSurfaceVariant = Color(0xFF54707B),
    outline = Color(0xFF9DBFC8), outlineVariant = Color(0xFFD1E4E9), error = Color(0xFFB64259)
)
private val GlassDark = darkColorScheme(
    primary = Color(0xFF78E5E5), onPrimary = Color(0xFF00383C),
    primaryContainer = Color(0xFF07535A), onPrimaryContainer = Color(0xFFC8FAFA),
    secondary = Color(0xFFB9C0FF), onSecondary = Color(0xFF292F69),
    secondaryContainer = Color(0xFF3D4686), onSecondaryContainer = Color(0xFFE3E6FF),
    tertiary = Color(0xFFF0A9D2), background = Color(0xFF071923), onBackground = Color(0xFFE5F8FC),
    surface = Color(0xFF102B38), onSurface = Color(0xFFE5F8FC),
    surfaceVariant = Color(0xFF1A3B49), onSurfaceVariant = Color(0xFFB1D1DA),
    outline = Color(0xFF4C7783), outlineVariant = Color(0xFF254653), error = Color(0xFFFF91A5)
)
private val LiquidLight = lightColorScheme(
    primary = Color(0xFF6156D9), onPrimary = Color.White,
    primaryContainer = Color(0xFFE2DEFF), onPrimaryContainer = Color(0xFF21195C),
    secondary = Color(0xFF008D9A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F4F2), onSecondaryContainer = Color(0xFF003B40),
    tertiary = Color(0xFFB65D91), background = Color(0xFFF2F1FA), onBackground = Color(0xFF22243D),
    surface = Color(0xFFFCFBFF), onSurface = Color(0xFF22243D),
    surfaceVariant = Color(0xFFE9E7F5), onSurfaceVariant = Color(0xFF66657F),
    outline = Color(0xFFB7B2D1), outlineVariant = Color(0xFFDEDBEC), error = Color(0xFFBC435F)
)
private val LiquidDark = darkColorScheme(
    primary = Color(0xFFB8AEFF), onPrimary = Color(0xFF2A216B),
    primaryContainer = Color(0xFF49409A), onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = Color(0xFF6EE4E1), onSecondary = Color(0xFF003B40),
    secondaryContainer = Color(0xFF07565B), onSecondaryContainer = Color(0xFFC5FBF8),
    tertiary = Color(0xFFF0A7D0), background = Color(0xFF111226), onBackground = Color(0xFFF0EEFF),
    surface = Color(0xFF1C1D35), onSurface = Color(0xFFF0EEFF),
    surfaceVariant = Color(0xFF2B2C49), onSurfaceVariant = Color(0xFFC2C0DE),
    outline = Color(0xFF65658F), outlineVariant = Color(0xFF393A5A), error = Color(0xFFFF9AAD)
)

private val AiKhataTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 37.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.2.sp)
)

@Composable
fun AiKhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    visualStyle: String = "liquid",
    content: @Composable () -> Unit
) {
    val colors = when (visualStyle) {
        "soft" -> if (darkTheme) SoftDark else SoftLight
        "glass" -> if (darkTheme) GlassDark else GlassLight
        else -> if (darkTheme) LiquidDark else LiquidLight
    }
    MaterialTheme(colorScheme = colors, typography = AiKhataTypography, content = content)
}
