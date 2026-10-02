package com.galandras12.handdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.galandras12.handdroid.data.ThemeMode

// Minimalist grey / white palette (light) and grey / black palette (dark). Dynamic colour is deliberately not used.
private val LightColors = lightColorScheme(
    primary = Color(0xFF454A52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E6EA),
    onPrimaryContainer = Color(0xFF1B1D21),
    secondary = Color(0xFF6A7079),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEEF1),
    onSecondaryContainer = Color(0xFF22252A),
    tertiary = Color(0xFF7A6A3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E8CE),
    onTertiaryContainer = Color(0xFF2B2310),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1D21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1D21),
    surfaceVariant = Color(0xFFEFF0F2),
    onSurfaceVariant = Color(0xFF4F535A),
    surfaceTint = Color(0xFF454A52),
    surfaceDim = Color(0xFFDFE1E4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F8),
    surfaceContainer = Color(0xFFF1F2F4),
    surfaceContainerHigh = Color(0xFFEBECEF),
    surfaceContainerHighest = Color(0xFFE4E6E9),
    outline = Color(0xFF8A8F97),
    outlineVariant = Color(0xFFD3D6DA),
    inverseSurface = Color(0xFF2E3136),
    inverseOnSurface = Color(0xFFF1F2F4),
    inversePrimary = Color(0xFFC9CDD3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCDD1D7),
    onPrimary = Color(0xFF1E2024),
    primaryContainer = Color(0xFF34373C),
    onPrimaryContainer = Color(0xFFE4E6EA),
    secondary = Color(0xFFADB2BA),
    onSecondary = Color(0xFF212328),
    secondaryContainer = Color(0xFF2B2E33),
    onSecondaryContainer = Color(0xFFDADDE1),
    tertiary = Color(0xFFD9C48A),
    onTertiary = Color(0xFF3A2F0E),
    tertiaryContainer = Color(0xFF4F4320),
    onTertiaryContainer = Color(0xFFF1E8CE),
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF0F1011),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF26282C),
    onSurfaceVariant = Color(0xFFB5B9BF),
    surfaceTint = Color(0xFFCDD1D7),
    surfaceDim = Color(0xFF0B0B0C),
    surfaceBright = Color(0xFF2E3033),
    surfaceContainerLowest = Color(0xFF070708),
    surfaceContainerLow = Color(0xFF151618),
    surfaceContainer = Color(0xFF1A1B1E),
    surfaceContainerHigh = Color(0xFF222428),
    surfaceContainerHighest = Color(0xFF2A2C30),
    outline = Color(0xFF8B9097),
    outlineVariant = Color(0xFF3A3D42),
    inverseSurface = Color(0xFFE6E8EB),
    inverseOnSurface = Color(0xFF2E3136),
    inversePrimary = Color(0xFF454A52),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HandDroidTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = AppShapes, content = content)
}
