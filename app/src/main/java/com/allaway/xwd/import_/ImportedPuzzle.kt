package com.allaway.xwd.import_

import com.allaway.xwd.model.Clue
import com.allaway.xwd.model.Direction
import com.allaway.xwd.model.GridBuilder
import com.allaway.xwd.model.Puzzle
import kotlinx.serialization.Serializable

/** Claude's structured response for a photographed crossword. */
@Serializable
data class ImportedClue(val number: Int, val clue: String, val answer: String)

@Serializable
data class ImportedPuzzle(
    val ok: Boolean,
    val error: String = "",
    val title: String = "",
    val author: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val grid: List<String> = emptyList(),
    val across: List<ImportedClue> = emptyList(),
    val down: List<ImportedClue> = emptyList(),
)

class ImportException(message: String) : Exception(message)

/**
 * Validates the model's output and converts it to a [Puzzle]. The grid is
 * the source of truth; clue answers are cross-checked against it and
 * disagreements are returned as warnings.
 */
object ImportConverter {

    data class Converted(val puzzle: Puzzle, val warnings: List<String>)

    fun convert(imported: ImportedPuzzle): Converted {
        if (!imported.ok) {
            throw ImportException(imported.error.ifBlank { "The image could not be read as a crossword." })
        }
        if (imported.width !in 2..40 || imported.height !in 2..40) {
            throw ImportException("Implausible grid size ${imported.width}x${imported.height}.")
        }
        if (imported.grid.size != imported.height) {
            throw ImportException("Grid has ${imported.grid.size} rows, expected ${imported.height}.")
        }
        val solution = buildString {
            imported.grid.forEachIndexed { r, row ->
                if (row.length != imported.width) {
                    throw ImportException("Grid row ${r + 1} has ${row.length} cells, expected ${imported.width}.")
                }
                row.forEach { ch ->
                    when {
                        ch == '.' || ch == '#' -> append('.')
                        ch.uppercaseChar() in 'A'..'Z' -> append(ch.uppercaseChar())
                        else -> throw ImportException("Grid row ${r + 1} contains invalid cell '$ch'.")
                    }
                }
            }
        }

        val built = GridBuilder.build(solution, imported.width, imported.height)
        val byKey = HashMap<Pair<Int, Direction>, ImportedClue>()
        imported.across.forEach { byKey[it.number to Direction.ACROSS] = it }
        imported.down.forEach { byKey[it.number to Direction.DOWN] = it }

        val warnings = ArrayList<String>()
        val clues = built.starts.map { start ->
            val key = start.number to start.direction
            val importedClue = byKey.remove(key)
                ?: throw ImportException(
                    "The grid implies clue ${start.number}-${start.direction.letter()} " +
                        "but no such clue was read from the image.",
                )
            val cells = GridBuilder.wordCells(
                start.cellIndex, start.direction, solution, imported.width, imported.height,
            )
            val gridAnswer = cells.map { solution[it] }.joinToString("")
            val claimedAnswer = importedClue.answer.uppercase().filter { it in 'A'..'Z' }
            if (claimedAnswer.isNotEmpty() && claimedAnswer != gridAnswer) {
                warnings.add(
                    "${start.number}-${start.direction.letter()}: answer “$claimedAnswer” " +
                        "doesn't match the grid (“$gridAnswer”).",
                )
            }
            Clue(start.number, start.direction, importedClue.clue, cells)
        }
        byKey.keys.forEach { (number, dir) ->
            warnings.add("Clue $number-${dir.letter()} was read from the image but doesn't fit the grid.")
        }
        // Widespread disagreement means the grid itself was probably misread.
        if (warnings.size > clues.size / 4) {
            throw ImportException(
                "The reading is too inconsistent to trust (${warnings.size} conflicts in " +
                    "${clues.size} clues). Try a sharper, straight-on photo.",
            )
        }

        val puzzle = Puzzle(
            title = imported.title.ifBlank { "Imported crossword" },
            author = imported.author,
            copyright = "",
            notes = "Imported from a photo; the solution was reconstructed by AI and may contain errors.",
            width = imported.width,
            height = imported.height,
            cells = built.cells,
            clues = clues,
        )
        return Converted(puzzle, warnings)
    }

    private fun Direction.letter() = if (this == Direction.ACROSS) "A" else "D"
}
