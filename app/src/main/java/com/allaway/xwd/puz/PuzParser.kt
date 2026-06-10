package com.allaway.xwd.puz

import com.allaway.xwd.model.Cell
import com.allaway.xwd.model.Clue
import com.allaway.xwd.model.Direction
import com.allaway.xwd.model.Puzzle
import java.nio.charset.Charset

class PuzFormatException(message: String) : Exception(message)

/**
 * Parser for the Across Lite .puz binary format, the de facto standard for
 * distributed crossword puzzles (WSJ, Universal, Jonesin', etc.).
 *
 * Format reference: https://code.google.com/archive/p/puz/wikis/FileFormat.wiki
 */
object PuzParser {

    private const val MAGIC = "ACROSS&DOWN"
    private val CHARSET: Charset = Charset.forName("windows-1252")

    private const val OFFSET_MAGIC = 0x02
    private const val OFFSET_WIDTH = 0x2C
    private const val OFFSET_HEIGHT = 0x2D
    private const val OFFSET_NUM_CLUES = 0x2E
    private const val OFFSET_SCRAMBLED = 0x32
    private const val OFFSET_BOARD = 0x34

    fun parse(bytes: ByteArray): Puzzle {
        if (bytes.size < OFFSET_BOARD) throw PuzFormatException("File too short (${bytes.size} bytes)")
        val magic = String(bytes, OFFSET_MAGIC, MAGIC.length, CHARSET)
        if (magic != MAGIC) throw PuzFormatException("Not an Across Lite file (bad magic)")

        val width = bytes[OFFSET_WIDTH].toInt() and 0xFF
        val height = bytes[OFFSET_HEIGHT].toInt() and 0xFF
        if (width == 0 || height == 0) throw PuzFormatException("Empty grid ${width}x$height")
        val numClues = readShortLE(bytes, OFFSET_NUM_CLUES)
        val scrambled = readShortLE(bytes, OFFSET_SCRAMBLED) != 0

        val area = width * height
        if (bytes.size < OFFSET_BOARD + 2 * area) throw PuzFormatException("Truncated grid data")
        val solution = String(bytes, OFFSET_BOARD, area, CHARSET)

        // Strings section: title, author, copyright, clues..., notes - NUL separated.
        var pos = OFFSET_BOARD + 2 * area
        val strings = ArrayList<String>(numClues + 4)
        while (strings.size < numClues + 4 && pos < bytes.size) {
            var end = pos
            while (end < bytes.size && bytes[end].toInt() != 0) end++
            strings.add(String(bytes, pos, end - pos, CHARSET))
            pos = end + 1
        }
        if (strings.size < numClues + 3) {
            throw PuzFormatException("Expected ${numClues + 3} strings, found ${strings.size}")
        }
        val title = strings[0]
        val author = strings[1]
        val copyright = strings[2]
        val clueTexts = strings.subList(3, 3 + numClues)
        val notes = strings.getOrElse(3 + numClues) { "" }

        val circled = parseCircles(bytes, pos, area)

        // Compute numbering from grid topology.
        val cells = ArrayList<Cell>(area)
        var number = 1
        val acrossStarts = ArrayList<Pair<Int, Int>>() // (number, index)
        val downStarts = ArrayList<Pair<Int, Int>>()
        for (i in 0 until area) {
            val ch = solution[i]
            if (ch == '.') {
                cells.add(Cell(solution = null))
                continue
            }
            val row = i / width
            val col = i % width
            val blockLeft = col == 0 || solution[i - 1] == '.'
            val openRight = col + 1 < width && solution[i + 1] != '.'
            val blockUp = row == 0 || solution[i - width] == '.'
            val openDown = row + 1 < height && solution[i + width] != '.'
            val startsAcross = blockLeft && openRight
            val startsDown = blockUp && openDown
            var cellNumber = 0
            if (startsAcross || startsDown) {
                cellNumber = number++
                if (startsAcross) acrossStarts.add(cellNumber to i)
                if (startsDown) downStarts.add(cellNumber to i)
            }
            cells.add(Cell(solution = ch.uppercaseChar(), number = cellNumber, circled = circled[i]))
        }

        // .puz lists clues in cell-number order, across before down on shared numbers.
        val starts = (acrossStarts.map { Triple(it.first, Direction.ACROSS, it.second) } +
            downStarts.map { Triple(it.first, Direction.DOWN, it.second) })
            .sortedWith(compareBy({ it.first }, { it.second }))
        if (starts.size != numClues) {
            throw PuzFormatException("Grid implies ${starts.size} clues but file declares $numClues")
        }
        val clues = starts.mapIndexed { i, (num, dir, start) ->
            Clue(num, dir, clueTexts[i], wordCells(start, dir, solution, width, height))
        }

        return Puzzle(
            title = title,
            author = author,
            copyright = copyright,
            notes = notes,
            width = width,
            height = height,
            cells = cells,
            clues = clues,
            scrambled = scrambled,
        )
    }

    private fun wordCells(start: Int, dir: Direction, solution: String, width: Int, height: Int): List<Int> {
        val step = if (dir == Direction.ACROSS) 1 else width
        val result = ArrayList<Int>()
        var i = start
        while (i < solution.length && solution[i] != '.') {
            result.add(i)
            if (dir == Direction.ACROSS && i % width == width - 1) break
            if (dir == Direction.DOWN && i / width == height - 1) break
            i += step
        }
        return result
    }

    /** Scan the extra sections that follow the strings for GEXT circle flags. */
    private fun parseCircles(bytes: ByteArray, sectionsStart: Int, area: Int): BooleanArray {
        val circled = BooleanArray(area)
        var pos = sectionsStart
        while (pos + 8 <= bytes.size) {
            val name = String(bytes, pos, 4, CHARSET)
            val len = readShortLE(bytes, pos + 4)
            val dataStart = pos + 8
            if (dataStart + len > bytes.size) break
            if (name == "GEXT" && len == area) {
                for (i in 0 until area) {
                    circled[i] = (bytes[dataStart + i].toInt() and 0x80) != 0
                }
            }
            pos = dataStart + len + 1 // sections are NUL-terminated
        }
        return circled
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
