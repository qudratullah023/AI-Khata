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

private val LightColors = lightColorScheme(
    primary = Color(0xFF087C70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F4E9),
    onPrimaryContainer = Color(0xFF00251F),
    secondary = Color(0xFF5369C8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E6FF),
    onSecondaryContainer = Color(0xFF111E54),
    tertiary = Color(0xFF9B620E),
    background = Color(0xFFF1F6FA),
    onBackground = Color(0xFF13202E),
    surface = Color(0xFFFAFCFF),
    onSurface = Color(0xFF13202E),
    surfaceVariant = Color(0xFFE3EBF2),
    onSurfaceVariant = Color(0xFF536477),
    outline = Color(0xFF9BAFBE),
    outlineVariant = Color(0xFFD2DDE6),
    error = Color(0xFFBE4557)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF57E2C1),
    onPrimary = Color(0xFF002C25),
    primaryContainer = Color(0xFF0B4A40),
    onPrimaryContainer = Color(0xFFB7F7E8),
    secondary = Color(0xFFA8B5FF),
    onSecondary = Color(0xFF1B2458),
    secondaryContainer = Color(0xFF303D7B),
    onSecondaryContainer = Color(0xFFE1E5FF),
    tertiary = Color(0xFFFFC778),
    background = Color(0xFF080F18),
    onBackground = Color(0xFFEAF4FF),
    surface = Color(0xFF111C29),
    onSurface = Color(0xFFEAF4FF),
    surfaceVariant = Color(0xFF1C2B3C),
    onSurfaceVariant = Color(0xFFB0C1D2),
    outline = Color(0xFF496174),
    outlineVariant = Color(0xFF293B4D),
    error = Color(0xFFFF8B9B)
)

private val AiKhataTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun AiKhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AiKhataTypography,
        content = content
    )
}
