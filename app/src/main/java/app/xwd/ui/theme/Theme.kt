package app.xwd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private fun shapesFor(skin: Skin) = when (skin) {
    // Margins: barely-rounded paper corners.
    Skin.MARGINS -> Shapes(
        extraSmall = RoundedCornerShape(3.dp),
        small = RoundedCornerShape(3.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
    // Terminal: tight 4dp panels.
    Skin.TERMINAL -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(6.dp),
    )
    // Overprint: chunky zine corners.
    Skin.OVERPRINT -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(18.dp),
    )
}

@Composable
fun XwdTheme(skin: Skin, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(
            colorScheme = skin.materialColors(),
            shapes = shapesFor(skin),
            typography = skin.materialTypography(),
            content = content,
        )
    }
}
