package app.xwd.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A 1px dotted leader line, as used all over the Margins skin. */
@Composable
fun DottedRule(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
            },
    )
}

/** Dashed rounded-rect border (Margins' ghost cards). */
fun Modifier.dashedBorder(color: Color, shape: Shape, width: Dp = 1.dp): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            color = color,
            style = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
            ),
        )
    }

/** Dashed circular border (Overprint's rubber stamps). */
fun Modifier.dashedCircleBorder(color: Color, width: Dp = 2.dp): Modifier =
    drawBehind {
        drawCircle(
            color = color,
            radius = (size.minDimension - width.toPx()) / 2,
            style = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            ),
        )
    }

/**
 * A hard offset shadow plate behind the content — the riso "off-register"
 * print effect (`box-shadow: 4px 4px 0` in the design).
 */
fun Modifier.offsetShadow(
    color: Color,
    shape: Shape,
    dx: Dp = 4.dp,
    dy: Dp = 4.dp,
): Modifier = drawBehind {
    translate(left = dx.toPx(), top = dy.toPx()) {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color)
    }
}
