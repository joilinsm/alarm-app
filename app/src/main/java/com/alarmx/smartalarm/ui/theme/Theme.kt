package com.alarmx.smartalarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

val Navy = Color(0xFF070B14)
val Charcoal = Color(0xFF121826)
val Card = Color(0xFF182033)
val Accent = Color(0xFFF5C16C)
val AccentSoft = Color(0xFF7DD3C0)
val Danger = Color(0xFFFF6B6B)
val TextPrimary = Color(0xFFF4F7FF)
val TextMuted = Color(0xFF9AA6C1)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1304),
    secondary = AccentSoft,
    onSecondary = Color(0xFF04221C),
    background = Navy,
    onBackground = TextPrimary,
    surface = Charcoal,
    onSurface = TextPrimary,
    surfaceVariant = Card,
    onSurfaceVariant = TextMuted,
    error = Danger,
    onError = Color.White,
    outline = Color(0xFF3A4663)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A12),
    onPrimary = Color.White,
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFE8E2D6),
    onSurfaceVariant = Color(0xFF4B5563),
    error = Color(0xFFB91C1C),
    onError = Color.White
)

val AlarmTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun AlarmXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AlarmTypography,
        content = content
    )
}
