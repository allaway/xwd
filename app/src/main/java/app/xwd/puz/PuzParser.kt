package app.xwd.puz

import app.xwd.model.Clue
import app.xwd.model.GridBuilder
import app.xwd.model.Puzzle
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

        val built = try {
            GridBuilder.build(solution, width, height, circled)
        } catch (e: IllegalArgumentException) {
            throw PuzFormatException(e.message ?: "Invalid grid")
        }
        if (built.starts.size != numClues) {
            throw PuzFormatException("Grid implies ${built.starts.size} clues but file declares $numClues")
        }
        val clues = built.starts.mapIndexed { i, start ->
            Clue(
                start.number,
                start.direction,
                clueTexts[i],
                GridBuilder.wordCells(start.cellIndex, start.direction, solution, width, height),
            )
        }

        return Puzzle(
            title = title,
            author = author,
            copyright = copyright,
            notes = notes,
            width = width,
            height = height,
            cells = built.cells,
            clues = clues,
            scrambled = scrambled,
        )
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
