package com.allaway.xwd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Grid colors: the puzzle itself always renders as white paper with black ink,
// in both app themes, like newsprint.
val SelectedCell = Color(0xFFFFD54F)
val CurrentWord = Color(0xFFB3E5FC)
val WrongLetter = Color(0xFFD32F2F)
val RevealedLetter = Color(0xFF1565C0)
val GridLine = Color(0xFF424242)
val BlockColor = Color(0xFF1B1B1B)

// One desaturated ink-blue accent on cool neutrals; no pure black or white.
private val LightColors = lightColorScheme(
    primary = Color(0xFF35507A),
    onPrimary = Color(0xFFFBFCFE),
    primaryContainer = Color(0xFFD8E2F4),
    onPrimaryContainer = Color(0xFF16263F),
    secondary = Color(0xFF55657E),
    onSecondary = Color(0xFFFBFCFE),
    secondaryContainer = Color(0xFFDDE4F0),
    onSecondaryContainer = Color(0xFF1B2535),
    tertiary = Color(0xFF5E5A71),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFBFBFC),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE7EAEF),
    onSurfaceVariant = Color(0xFF454B55),
    outline = Color(0xFF747B87),
    outlineVariant = Color(0xFFD2D7DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C2E8),
    onPrimary = Color(0xFF0F2342),
    primaryContainer = Color(0xFF2A3F5F),
    onPrimaryContainer = Color(0xFFD8E2F4),
    secondary = Color(0xFFBCC7DA),
    onSecondary = Color(0xFF26303F),
    secondaryContainer = Color(0xFF3C4757),
    onSecondaryContainer = Color(0xFFD8E0EE),
    tertiary = Color(0xFFC7C2DC),
    background = Color(0xFF121418),
    onBackground = Color(0xFFE1E2E7),
    surface = Color(0xFF17191E),
    onSurface = Color(0xFFE1E2E7),
    surfaceVariant = Color(0xFF23262D),
    onSurfaceVariant = Color(0xFFC2C7D0),
    outline = Color(0xFF8B919C),
    outlineVariant = Color(0xFF3C4047),
)

private val XwdShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val XwdTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        labelMedium = base.labelMedium.copy(letterSpacing = 0.3.sp),
    )
}

@Composable
fun XwdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = XwdShapes,
        typography = XwdTypography,
        content = content,
    )
}
