package app.xwd.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xwd.ui.theme.LocalSkin
import app.xwd.ui.theme.MarginsT
import app.xwd.ui.theme.RisoT
import app.xwd.ui.theme.Skin
import app.xwd.ui.theme.TermT
import app.xwd.ui.theme.fontFamily

private val ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Immutable
private data class KeyStyle(
    val boardBg: Color,
    val keyBg: Color,
    val keyText: Color,
    val backBg: Color,
    val backText: Color,
    val border: Color?,
    val borderWidth: Dp,
    val shadow: Color?,
    val corner: Dp,
    val weight: FontWeight,
)

private fun keyStyleFor(skin: Skin): KeyStyle = when (skin) {
    // Paper keys with a crisp 1px bottom shadow, serif caps.
    Skin.MARGINS -> KeyStyle(
        boardBg = MarginsT.bg,
        keyBg = MarginsT.card,
        keyText = MarginsT.body,
        backBg = MarginsT.card,
        backText = MarginsT.muted,
        border = MarginsT.keyBorder,
        borderWidth = 1.dp,
        shadow = MarginsT.keyShadow,
        corner = 7.dp,
        weight = FontWeight.Normal,
    )
    // Flat phosphor keys; amber backspace glyph.
    Skin.TERMINAL -> KeyStyle(
        boardBg = TermT.bg,
        keyBg = TermT.keyBg,
        keyText = TermT.mid,
        backBg = TermT.keyBg,
        backText = TermT.amber,
        border = TermT.keyBorder,
        borderWidth = 1.dp,
        shadow = null,
        corner = 4.dp,
        weight = FontWeight.Normal,
    )
    // Inked outlines with the pink offset shadow; backspace prints inverted.
    Skin.OVERPRINT -> KeyStyle(
        boardBg = RisoT.bg,
        keyBg = RisoT.paper,
        keyText = RisoT.blue,
        backBg = RisoT.blue,
        backText = RisoT.paper,
        border = RisoT.blue,
        borderWidth = 2.dp,
        shadow = RisoT.pinkShadow,
        corner = 8.dp,
        weight = FontWeight.Bold,
    )
}

@Composable
fun CrosswordKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val style = keyStyleFor(skin)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(style.boardBg)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ROWS.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (rowIndex == 1) Spacer(0.5f)
                row.forEach { ch ->
                    Key(style, bg = style.keyBg, modifier = Modifier.weight(1f), onClick = { onKey(ch) }) {
                        Text(
                            ch.toString(),
                            fontSize = 17.sp,
                            fontWeight = style.weight,
                            fontFamily = skin.fontFamily,
                            color = style.keyText,
                        )
                    }
                }
                if (rowIndex == 1) Spacer(0.5f)
                if (rowIndex == 2) {
                    Key(style, bg = style.backBg, modifier = Modifier.weight(1.5f), onClick = onBackspace) {
                        Text(
                            "⌫",
                            fontSize = 17.sp,
                            fontFamily = skin.fontFamily,
                            color = style.backText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Spacer(weight: Float) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(weight))
}

@Composable
private fun Key(
    style: KeyStyle,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(style.corner)
    Box(modifier) {
        // Offset shadow plate behind the key (Margins' 1px drop, Overprint's riso plate).
        style.shadow?.let {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = 1.5.dp, y = 2.dp)
                    .background(it, shape),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(bg)
                .let { m -> style.border?.let { m.border(style.borderWidth, it, shape) } ?: m }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
