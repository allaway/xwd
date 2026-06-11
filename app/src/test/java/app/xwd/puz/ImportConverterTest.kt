package app.xwd.puz

import app.xwd.import_.ImportConverter
import app.xwd.import_.ImportException
import app.xwd.import_.ImportedClue
import app.xwd.import_.ImportedPuzzle
import app.xwd.import_.PhotoImporter
import app.xwd.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportConverterTest {

    // 3x3, block bottom-left:
    //   C A B
    //   A G O
    //   . E N
    private fun imported(
        grid: List<String> = listOf("CAB", "AGO", ".EN"),
        across: List<ImportedClue> = listOf(
            ImportedClue(1, "Taxi", "CAB"),
            ImportedClue(4, "In the past", "AGO"),
            ImportedClue(5, "Half an em", "EN"),
        ),
        down: List<ImportedClue> = listOf(
            ImportedClue(1, "Calcium, for short", "CA"),
            ImportedClue(2, "Time of life", "AGE"),
            ImportedClue(3, "James ___ Jones", "BON"),
        ),
        ok: Boolean = true,
        error: String = "",
    ) = ImportedPuzzle(
        ok = ok, error = error, title = "Mini", author = "Tester",
        width = 3, height = 3, grid = grid, across = across, down = down,
    )

    @Test
    fun convertsConsistentPuzzle() {
        val result = ImportConverter.convert(imported())
        assertEquals(3, result.puzzle.width)
        assertEquals(6, result.puzzle.clues.size)
        assertTrue(result.warnings.isEmpty())
        val oneAcross = result.puzzle.clues.first { it.number == 1 && it.direction == Direction.ACROSS }
        assertEquals("Taxi", oneAcross.text)
        assertEquals(listOf(0, 1, 2), oneAcross.cells)
        assertEquals('C', result.puzzle.cellAt(0, 0).solution)
        assertTrue(result.puzzle.cellAt(2, 0).isBlock)
    }

    @Test
    fun normalizesHashBlocksAndLowercase() {
        val result = ImportConverter.convert(imported(grid = listOf("cab", "ago", "#en")))
        assertTrue(result.puzzle.cellAt(2, 0).isBlock)
        assertEquals('N', result.puzzle.cellAt(2, 2).solution)
    }

    @Test
    fun flagsAnswerGridDisagreementAsWarning() {
        val result = ImportConverter.convert(
            imported(
                across = listOf(
                    ImportedClue(1, "Taxi", "CAR"), // grid says CAB
                    ImportedClue(4, "In the past", "AGO"),
                    ImportedClue(5, "Half an em", "EN"),
                ),
            ),
        )
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("CAR"))
    }

    @Test(expected = ImportException::class)
    fun rejectsMissingClue() {
        ImportConverter.convert(
            imported(across = listOf(ImportedClue(1, "Taxi", "CAB"))),
        )
    }

    @Test(expected = ImportException::class)
    fun rejectsWrongRowLength() {
        ImportConverter.convert(imported(grid = listOf("CABX", "AGO", ".EN")))
    }

    @Test(expected = ImportException::class)
    fun rejectsModelReportedFailure() {
        ImportConverter.convert(imported(ok = false, error = "Not a crossword"))
    }

    @Test
    fun parsesStructuredResponseJson() {
        val json = """
            {"ok":true,"error":"","title":"Mini","author":"A","width":3,"height":3,
             "grid":["CAB","AGO",".EN"],
             "across":[{"number":1,"clue":"Taxi","answer":"CAB"},
                       {"number":4,"clue":"In the past","answer":"AGO"},
                       {"number":5,"clue":"Half an em","answer":"EN"}],
             "down":[{"number":1,"clue":"Calcium","answer":"CA"},
                     {"number":2,"clue":"Time of life","answer":"AGE"},
                     {"number":3,"clue":"James ___ Jones","answer":"BON"}]}
        """.trimIndent()
        val parsed = PhotoImporter.parseResponse(json)
        assertTrue(parsed.ok)
        assertEquals(3, parsed.across.size)
        val converted = ImportConverter.convert(parsed)
        assertEquals(6, converted.puzzle.clues.size)
    }

    @Test(expected = ImportException::class)
    fun parseRejectsNonJson()  {
        PhotoImporter.parseResponse("I'm sorry, I can't read this image.")
    }

    @Test
    fun responseSchemaBuilds() {
        val schema = PhotoImporter.responseSchema()
        assertTrue(schema._additionalProperties().containsKey("properties"))
    }
}
