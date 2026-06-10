package com.allaway.xwd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SelectedCell = Color(0xFFFFD54F)
val CurrentWord = Color(0xFFB3E5FC)
val WrongLetter = Color(0xFFD32F2F)
val RevealedLetter = Color(0xFF1565C0)
val GridLine = Color(0xFF424242)
val BlockColor = Color(0xFF1B1B1B)

private val LightColors = lightColorScheme(
    primary = Color(0xFF35507A),
    secondary = Color(0xFF5C7299),
    tertiary = Color(0xFF7D5260),
)

@Composable
fun XwdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
