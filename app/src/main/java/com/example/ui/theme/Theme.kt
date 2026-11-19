package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = EmeraldSecondary,
    onSecondary = EmeraldOnSecondary,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldOnContainer,
    tertiary = AmberTertiary,
    onTertiary = Color(0xFF281900),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    background = ObsidianBg,
    onBackground = Color(0xFFE2E8F0),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1E293B),
    error = ErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = EmeraldSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = EmeraldOnContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,
    background = DaylightBg,
    onBackground = Color(0xFF0F172A),
    surface = DaylightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CodeLensTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
