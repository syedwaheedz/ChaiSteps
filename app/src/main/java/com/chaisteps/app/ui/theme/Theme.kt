package com.chaisteps.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One vivid colour per metric, readable on white and on black. */
object Accents {
    val steps = Color(0xFF12B886)
    val walk = Color(0xFF2F7BF6)
    val weight = Color(0xFF8B5CF6)
    val chai = Color(0xFFF08C00)
    val route = Color(0xFF2F7BF6)
    val danger = Color(0xFFEF4444)
}

private val Light = lightColorScheme(
    primary = Color(0xFF1C1C1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEDF0),
    onPrimaryContainer = Color(0xFF1C1C1E),
    secondary = Color(0xFF12B886),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F5EA),
    onSecondaryContainer = Color(0xFF00382A),
    tertiary = Color(0xFFEF4444),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDE2E2),
    onTertiaryContainer = Color(0xFF5C0A0A),
    background = Color(0xFFF4F4F6),
    onBackground = Color(0xFF111113),
    surface = Color(0xFFF4F4F6),
    onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFE9E9EC),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F0F3),
    surfaceContainerHighest = Color(0xFFE6E6EA),
    outline = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFFE3E3E7),
    error = Color(0xFFD92D20),
)

private val Dark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2C2C2E),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF34D3A0),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF0E3B2E),
    onSecondaryContainer = Color(0xFFB9F3DE),
    tertiary = Color(0xFFFF6B6B),
    onTertiary = Color(0xFF4A0000),
    tertiaryContainer = Color(0xFF5C1A1A),
    onTertiaryContainer = Color(0xFFFFDADA),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    surface = Color.Black,
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF0B0B0C),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF636366),
    outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFF6B6B),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

/** Big metric numbers. */
val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.8).sp)

@Composable
fun ChaiStepsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
