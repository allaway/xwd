package app.xwd.puz

import app.xwd.model.Clue
import app.xwd.model.Direction
import app.xwd.model.GridBuilder
import app.xwd.model.Puzzle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parser for the ipuz JSON crossword format (http://ipuz.org/), the open
 * standard used alongside .puz by many indie constructors and tools.
 *
 * Supports the crossword kind: dimensions, puzzle grid (numbers, blocks,
 * null voids, styled cells with circles), optional solution grid, and
 * Across/Down clue lists in both `[number, "clue"]` and object forms.
 */
object IpuzParser {

    fun parse(text: String): Puzzle {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw PuzFormatException("Not an ipuz file: ${e.message}")
        }

        val kinds = (root["kind"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        // Barred/diagramless variants parse as if they were block grids and
        // render as nonsense, so reject them outright rather than garble them.
        val unsupported = listOf("barred", "diagramless", "acrostic")
        if (kinds.none { it.contains("crossword", ignoreCase = true) } ||
            kinds.any { k -> unsupported.any { k.contains(it, ignoreCase = true) } }
        ) {
            throw PuzFormatException("Unsupported ipuz kind: $kinds")
        }

        val dims = root["dimensions"] as? JsonObject
            ?: throw PuzFormatException("ipuz file has no dimensions")
        val width = (dims["width"] as? JsonPrimitive)?.intOrNull
            ?: throw PuzFormatException("ipuz dimensions missing width")
        val height = (dims["height"] as? JsonPrimitive)?.intOrNull
            ?: throw PuzFormatException("ipuz dimensions missing height")
        if (width <= 0 || height <= 0) throw PuzFormatException("Empty grid ${width}x$height")

        val blockChar = (root["block"] as? JsonPrimitive)?.contentOrNull ?: "#"
        val emptyChar = (root["empty"] as? JsonPrimitive)?.contentOrNull ?: "0"

        val puzzleRows = root["puzzle"] as? JsonArray
            ?: throw PuzFormatException("ipuz file has no puzzle grid")
        if (puzzleRows.size != height) {
            throw PuzFormatException("Grid has ${puzzleRows.size} rows, expected $height")
        }

        val area = width * height
        val blocks = BooleanArray(area)
        val numbers = IntArray(area)
        val circled = BooleanArray(area)
        for (r in 0 until height) {
            val row = puzzleRows[r] as? JsonArray
                ?: throw PuzFormatException("Row $r is not an array")
            if (row.size != width) {
                throw PuzFormatException("Row $r has ${row.size} cells, expected $width")
            }
            for (c in 0 until width) {
                val i = r * width + c
                val cell = unwrapCell(row[c], circled, i)
                when {
                    cell == null -> blocks[i] = true // null = void; render as block
                    cell.contentOrNull == blockChar -> blocks[i] = true
                    cell.contentOrNull == emptyChar -> {} // unnumbered white cell
                    else -> numbers[i] = cell.intOrNull ?: 0 // labels stay unnumbered
                }
            }
        }

        // Optional solution grid; absent (e.g. contest puzzles) behaves like a
        // locked .puz: solvable, but check/reveal/completion are unavailable.
        val solutionRows = root["solution"] as? JsonArray
        val letters = CharArray(area) { if (blocks[it]) '.' else 'X' }
        var hasSolution = false
        if (solutionRows != null && solutionRows.size == height) {
            for (r in 0 until height) {
                val row = solutionRows[r] as? JsonArray ?: continue
                for (c in 0 until width.coerceAtMost(row.size)) {
                    val i = r * width + c
                    if (blocks[i]) continue
                    val value = solutionValue(row[c])
                    if (!value.isNullOrEmpty() && value != blockChar) {
                        letters[i] = value.first().uppercaseChar()
                        hasSolution = true
                    }
                }
            }
        }

        val built = try {
            GridBuilder.build(String(letters), width, height, circled)
        } catch (e: IllegalArgumentException) {
            throw PuzFormatException(e.message ?: "Invalid grid")
        }
        // Cross-check the file's own numbering where it declares any.
        for (i in 0 until area) {
            if (numbers[i] > 0 && built.cells[i].number != numbers[i]) {
                throw PuzFormatException(
                    "Cell $i numbered ${numbers[i]} but grid implies ${built.cells[i].number}",
                )
            }
        }

        val clueTexts = parseClues(root["clues"])
        val solutionString = String(letters)
        val clues = built.starts.map { start ->
            Clue(
                start.number,
                start.direction,
                clueTexts[start.direction to start.number] ?: "",
                GridBuilder.wordCells(start.cellIndex, start.direction, solutionString, width, height),
            )
        }

        return Puzzle(
            title = stringField(root, "title"),
            author = stringField(root, "author"),
            copyright = stringField(root, "copyright"),
            notes = stringField(root, "notes").ifBlank { stringField(root, "intro") },
            width = width,
            height = height,
            cells = built.cells,
            clues = clues,
            scrambled = !hasSolution,
        )
    }

    /**
     * A puzzle-grid cell is a primitive, null, or a StyledCell object like
     * `{"cell": 5, "style": {"shapebg": "circle"}}`. Returns the underlying
     * primitive (null for void cells) and records any circle style.
     */
    private fun unwrapCell(element: JsonElement, circled: BooleanArray, index: Int): JsonPrimitive? =
        when (element) {
            is JsonNull -> null
            is JsonPrimitive -> element
            is JsonObject -> {
                val style = element["style"] as? JsonObject
                if ((style?.get("shapebg") as? JsonPrimitive)?.contentOrNull == "circle") {
                    circled[index] = true
                }
                when (val inner = element["cell"]) {
                    null, is JsonNull -> null
                    is JsonPrimitive -> inner
                    else -> throw PuzFormatException("Unsupported cell at index $index")
                }
            }
            else -> throw PuzFormatException("Unsupported cell at index $index")
        }

    /** A solution cell is a letter, a block char, null, or `{"value": "A"}`. */
    private fun solutionValue(element: JsonElement): String? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> (element["value"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    /** Flattens the Across/Down clue lists into (direction, number) -> text. */
    private fun parseClues(element: JsonElement?): Map<Pair<Direction, Int>, String> {
        val cluesObj = element as? JsonObject ?: return emptyMap()
        val result = HashMap<Pair<Direction, Int>, String>()
        for ((key, list) in cluesObj) {
            val direction = when {
                key.startsWith("across", ignoreCase = true) -> Direction.ACROSS
                key.startsWith("down", ignoreCase = true) -> Direction.DOWN
                else -> continue
            }
            for (entry in (list as? JsonArray).orEmpty()) {
                val (number, clue) = when (entry) {
                    is JsonArray -> {
                        val num = (entry.getOrNull(0) as? JsonPrimitive)?.intOrNull
                        val text = (entry.getOrNull(1) as? JsonPrimitive)?.contentOrNull
                        num to text
                    }
                    is JsonObject -> {
                        val num = (entry["number"] as? JsonPrimitive)?.intOrNull
                        val text = (entry["clue"] as? JsonPrimitive)?.contentOrNull
                        num to text
                    }
                    else -> null to null
                }
                if (number != null && clue != null) result[direction to number] = clue
            }
        }
        return result
    }

    private fun stringField(root: JsonObject, key: String): String =
        (root[key] as? JsonPrimitive)?.contentOrNull ?: ""
}
