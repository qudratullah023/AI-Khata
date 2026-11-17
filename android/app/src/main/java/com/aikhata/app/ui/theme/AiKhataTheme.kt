package com.aikhata.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

const val STYLE_NEUMORPHISM = "neumorphism"
const val STYLE_GLASSMORPHISM = "glassmorphism"
const val STYLE_LIQUID_GLASS = "liquid_glass"

val LocalUiStyle = staticCompositionLocalOf { STYLE_LIQUID_GLASS }

private val NeumorphicLight = lightColorScheme(
    primary = Color(0xFF536B86), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E0EC), onPrimaryContainer = Color(0xFF1D3045),
    secondary = Color(0xFF718A9F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6EF), onSecondaryContainer = Color(0xFF263A4D),
    tertiary = Color(0xFF9A795E), background = Color(0xFFE7ECF2),
    onBackground = Color(0xFF263443), surface = Color(0xFFE7ECF2),
    onSurface = Color(0xFF263443), surfaceVariant = Color(0xFFDDE4EC),
    onSurfaceVariant = Color(0xFF5C6B7A), outline = Color(0xFFB7C2CE),
    outlineVariant = Color(0xFFD0D9E2), error = Color(0xFFB64F58)
)
private val NeumorphicDark = darkColorScheme(
    primary = Color(0xFFB7CBE0), onPrimary = Color(0xFF26384B),
    primaryContainer = Color(0xFF3B5066), onPrimaryContainer = Color(0xFFE0EBF6),
    secondary = Color(0xFFA8BDCF), onSecondary = Color(0xFF26394A),
    secondaryContainer = Color(0xFF3A5062), onSecondaryContainer = Color(0xFFE0EAF3),
    tertiary = Color(0xFFD7B99B), background = Color(0xFF252F3A),
    onBackground = Color(0xFFE5ECF3), surface = Color(0xFF2B3642),
    onSurface = Color(0xFFE5ECF3), surfaceVariant = Color(0xFF354351),
    onSurfaceVariant = Color(0xFFBAC8D5), outline = Color(0xFF526273),
    outlineVariant = Color(0xFF40505F), error = Color(0xFFFFA0A8)
)
private val GlassLight = lightColorScheme(
    primary = Color(0xFF426E9B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E9FB), onPrimaryContainer = Color(0xFF183552),
    secondary = Color(0xFF7864A8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E0FA), onSecondaryContainer = Color(0xFF33234F),
    tertiary = Color(0xFF3B8C91), background = Color(0xFFEAF2FA),
    onBackground = Color(0xFF172B40), surface = Color(0xFFF8FBFF),
    onSurface = Color(0xFF172B40), surfaceVariant = Color(0xFFDCE8F4),
    onSurfaceVariant = Color(0xFF536B82), outline = Color(0xFFB4C9DD),
    outlineVariant = Color(0xFFD1E0ED), error = Color(0xFFB94D61)
)
private val GlassDark = darkColorScheme(
    primary = Color(0xFF9CCBFA), onPrimary = Color(0xFF163653),
    primaryContainer = Color(0xFF294E70), onPrimaryContainer = Color(0xFFD9ECFF),
    secondary = Color(0xFFD0B8FF), onSecondary = Color(0xFF38285C),
    secondaryContainer = Color(0xFF4A3A70), onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFF8DD8D7), background = Color(0xFF0D1928),
    onBackground = Color(0xFFEAF3FC), surface = Color(0xFF18283B),
    onSurface = Color(0xFFEAF3FC), surfaceVariant = Color(0xFF263A50),
    onSurfaceVariant = Color(0xFFB5C7D9), outline = Color(0xFF4B6680),
    outlineVariant = Color(0xFF344A61), error = Color(0xFFFF9BAA)
)
private val LiquidLight = lightColorScheme(
    primary = Color(0xFF087F83), onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2EF), onPrimaryContainer = Color(0xFF003B3B),
    secondary = Color(0xFF6569C8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E2FF), onSecondaryContainer = Color(0xFF25265C),
    tertiary = Color(0xFFB16B9D), background = Color(0xFFF0F6FB),
    onBackground = Color(0xFF142638), surface = Color(0xFFF9FCFF),
    onSurface = Color(0xFF142638), surfaceVariant = Color(0xFFE2EDF5),
    onSurfaceVariant = Color(0xFF566B7E), outline = Color(0xFFB5C9D8),
    outlineVariant = Color(0xFFD6E2EB), error = Color(0xFFBD4C63)
)
private val LiquidDark = darkColorScheme(
    primary = Color(0xFF73E5D8), onPrimary = Color(0xFF003B38),
    primaryContainer = Color(0xFF075C58), onPrimaryContainer = Color(0xFFC5FFF8),
    secondary = Color(0xFFB8B9FF), onSecondary = Color(0xFF28295D),
    secondaryContainer = Color(0xFF41427D), onSecondaryContainer = Color(0xFFE5E5FF),
    tertiary = Color(0xFFFFA9D7), background = Color(0xFF091522),
    onBackground = Color(0xFFEAF6FF), surface = Color(0xFF142536),
    onSurface = Color(0xFFEAF6FF), surfaceVariant = Color(0xFF20384C),
    onSurfaceVariant = Color(0xFFB1C6D7), outline = Color(0xFF456579),
    outlineVariant = Color(0xFF2E485D), error = Color(0xFFFF9EAE)
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
    style: String = STYLE_LIQUID_GLASS,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val safeStyle = when (style) {
        STYLE_NEUMORPHISM, STYLE_GLASSMORPHISM -> style
        else -> STYLE_LIQUID_GLASS
    }
    val colors = when (safeStyle) {
        STYLE_NEUMORPHISM -> if (darkTheme) NeumorphicDark else NeumorphicLight
        STYLE_GLASSMORPHISM -> if (darkTheme) GlassDark else GlassLight
        else -> if (darkTheme) LiquidDark else LiquidLight
    }
    CompositionLocalProvider(LocalUiStyle provides safeStyle) {
        MaterialTheme(colorScheme = colors, typography = AiKhataTypography, content = content)
    }
}
