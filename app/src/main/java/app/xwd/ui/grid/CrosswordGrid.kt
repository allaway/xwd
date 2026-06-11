package app.xwd.ui.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import app.xwd.model.Puzzle
import app.xwd.ui.theme.BlockColor
import app.xwd.ui.theme.CurrentWord
import app.xwd.ui.theme.GridLine
import app.xwd.ui.theme.RevealedLetter
import app.xwd.ui.theme.SelectedCell
import app.xwd.ui.theme.WrongLetter
import kotlin.math.min

@Composable
fun CrosswordGrid(
    puzzle: Puzzle,
    letters: String,
    selected: Int,
    currentWord: List<Int>,
    isWrong: (Int) -> Boolean,
    revealed: Set<Int>,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val wordSet = currentWord.toSet()

    Canvas(
        modifier = modifier.pointerInput(puzzle) {
            detectTapGestures { offset ->
                val cellSize = min(size.width.toFloat() / puzzle.width, size.height.toFloat() / puzzle.height)
                val col = (offset.x / cellSize).toInt()
                val row = (offset.y / cellSize).toInt()
                if (row in 0 until puzzle.height && col in 0 until puzzle.width) {
                    onCellTap(row * puzzle.width + col)
                }
            }
        },
    ) {
        val cell = min(size.width / puzzle.width, size.height / puzzle.height)
        val letterStyle = TextStyle(
            fontSize = TextUnit(cell * 0.55f / density, TextUnitType.Sp),
            fontWeight = FontWeight.Medium,
        )
        val numberStyle = TextStyle(fontSize = TextUnit(cell * 0.24f / density, TextUnitType.Sp))

        for (i in puzzle.cells.indices) {
            val row = i / puzzle.width
            val col = i % puzzle.width
            val x = col * cell
            val y = row * cell
            val c = puzzle.cells[i]
            val topLeft = Offset(x, y)
            val boxSize = Size(cell, cell)

            if (c.isBlock) {
                drawRect(BlockColor, topLeft, boxSize)
                continue
            }
            val background = when {
                i == selected -> SelectedCell
                i in wordSet -> CurrentWord
                else -> Color.White
            }
            drawRect(background, topLeft, boxSize)

            if (c.circled) {
                drawCircle(
                    color = GridLine,
                    radius = cell * 0.46f,
                    center = Offset(x + cell / 2, y + cell / 2),
                    style = Stroke(width = cell * 0.03f),
                )
            }
            if (c.number > 0) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = c.number.toString(),
                    topLeft = Offset(x + cell * 0.06f, y + cell * 0.02f),
                    style = numberStyle,
                )
            }
            val ch = letters.getOrNull(i) ?: '-'
            if (ch != '-' && ch != '.') {
                val wrong = isWrong(i)
                val color = when {
                    wrong -> WrongLetter
                    i in revealed -> RevealedLetter
                    else -> Color.Black
                }
                val measured = textMeasurer.measure(ch.toString(), letterStyle)
                drawText(
                    textLayoutResult = measured,
                    color = color,
                    topLeft = Offset(
                        x + (cell - measured.size.width) / 2f,
                        y + cell * 0.92f - measured.size.height,
                    ),
                )
                if (wrong) {
                    // Diagonal slash across the cell, NYT-style error mark.
                    drawLine(
                        color = WrongLetter,
                        start = Offset(x + cell * 0.12f, y + cell * 0.88f),
                        end = Offset(x + cell * 0.88f, y + cell * 0.12f),
                        strokeWidth = cell * 0.04f,
                    )
                }
            }
        }
        // Grid lines on top.
        for (col in 0..puzzle.width) {
            val x = col * cell
            drawLine(GridLine, Offset(x, 0f), Offset(x, puzzle.height * cell), strokeWidth = 1.5f)
        }
        for (row in 0..puzzle.height) {
            val y = row * cell
            drawLine(GridLine, Offset(0f, y), Offset(puzzle.width * cell, y), strokeWidth = 1.5f)
        }
    }
}
