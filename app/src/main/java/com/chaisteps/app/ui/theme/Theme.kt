package com.chaisteps.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Accent colours for each area of the app, readable on both light and dark surfaces. */
object Accents {
    val steps = Color(0xFF2E8B57)
    val walk = Color(0xFF1E88E5)
    val weight = Color(0xFF8E44AD)
    val chai = Color(0xFFB5651D)
    val route = Color(0xFFE5533D)
}

private val Light = lightColorScheme(
    primary = Color(0xFF7A4A21),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC0),
    onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF55693A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8EFB4),
    onSecondaryContainer = Color(0xFF142004),
    tertiary = Color(0xFFA0402A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3D0600),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3DFD1),
    onSurfaceVariant = Color(0xFF52443A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E7),
    surfaceContainer = Color(0xFFFAEBE0),
    surfaceContainerHigh = Color(0xFFF4E5DA),
    surfaceContainerHighest = Color(0xFFEEDFD4),
    outline = Color(0xFF857468),
    outlineVariant = Color(0xFFD7C3B6),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2B989),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF61390F),
    onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFBCD39A),
    onSecondary = Color(0xFF283510),
    secondaryContainer = Color(0xFF3E5025),
    onSecondaryContainer = Color(0xFFD8EFB4),
    tertiary = Color(0xFFFFB4A3),
    onTertiary = Color(0xFF611300),
    tertiaryContainer = Color(0xFF822A16),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF19120D),
    onBackground = Color(0xFFEFE0D6),
    surface = Color(0xFF19120D),
    onSurface = Color(0xFFEFE0D6),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD7C3B6),
    surfaceContainerLowest = Color(0xFF130D08),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainer = Color(0xFF261E18),
    surfaceContainerHigh = Color(0xFF312822),
    surfaceContainerHighest = Color(0xFF3C332C),
    outline = Color(0xFFA08D81),
    outlineVariant = Color(0xFF52443A),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp)

@Composable
fun ChaiStepsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
