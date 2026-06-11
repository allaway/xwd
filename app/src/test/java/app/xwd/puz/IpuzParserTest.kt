package app.xwd.puz

import app.xwd.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IpuzParserTest {

    // 3x3 grid, block top-right; standard American numbering.
    private val basic = """
        {
          "version": "http://ipuz.org/v2",
          "kind": ["http://ipuz.org/crossword#1"],
          "title": "Test Ipuz",
          "author": "A. Setter",
          "copyright": "© 2026",
          "notes": "Hi",
          "dimensions": {"width": 3, "height": 3},
          "puzzle": [
            [1, 2, "#"],
            [3, 0, 4],
            [5, 0, 0]
          ],
          "solution": [
            ["C", "A", "#"],
            ["A", "B", "C"],
            ["T", "E", "D"]
          ],
          "clues": {
            "Across": [[1, "First across"], [3, "Second across"], [5, "Third across"]],
            "Down": [[1, "First down"], [2, "Second down"], [4, "Third down"]]
          }
        }
    """.trimIndent()

    @Test
    fun parsesBasicCrossword() {
        val p = IpuzParser.parse(basic)
        assertEquals("Test Ipuz", p.title)
        assertEquals("A. Setter", p.author)
        assertEquals("Hi", p.notes)
        assertEquals(3, p.width)
        assertEquals(3, p.height)
        assertTrue(p.cells[2].isBlock)
        assertEquals(1, p.cells[0].number)
        assertEquals(5, p.cells[6].number)
        assertEquals('C', p.cells[0].solution)
        assertEquals('D', p.cells[8].solution)
        assertFalse(p.scrambled)
        assertEquals(6, p.clues.size)
        val a3 = p.clues.first { it.direction == Direction.ACROSS && it.number == 3 }
        assertEquals("Second across", a3.text)
        assertEquals(listOf(3, 4, 5), a3.cells)
        val d2 = p.clues.first { it.direction == Direction.DOWN && it.number == 2 }
        assertEquals("Second down", d2.text)
        assertEquals(listOf(1, 4, 7), d2.cells)
    }

    @Test
    fun parsesStyledCellsObjectCluesAndValueSolutions() {
        val ipuz = """
            {
              "kind": ["http://ipuz.org/crossword#1"],
              "dimensions": {"width": 2, "height": 1},
              "puzzle": [[{"cell": 1, "style": {"shapebg": "circle"}}, 0]],
              "solution": [[{"value": "h"}, "I"]],
              "clues": {"Across": [{"number": 1, "clue": "Greeting"}]}
            }
        """.trimIndent()
        val p = IpuzParser.parse(ipuz)
        assertTrue(p.cells[0].circled)
        assertFalse(p.cells[1].circled)
        assertEquals('H', p.cells[0].solution)
        assertEquals('I', p.cells[1].solution)
        assertEquals("Greeting", p.clues.single().text)
    }

    @Test
    fun missingSolutionBehavesLikeLockedPuzzle() {
        val ipuz = """
            {
              "kind": ["http://ipuz.org/crossword#1"],
              "dimensions": {"width": 2, "height": 1},
              "puzzle": [[1, 0]],
              "clues": {"Across": [[1, "No peeking"]]}
            }
        """.trimIndent()
        val p = IpuzParser.parse(ipuz)
        assertTrue(p.scrambled)
        assertFalse(p.cells[0].isBlock)
    }

    @Test
    fun nullCellsAreVoids() {
        val ipuz = """
            {
              "kind": ["http://ipuz.org/crossword#1"],
              "dimensions": {"width": 2, "height": 1},
              "puzzle": [[null, 0]],
              "solution": [[null, "A"]]
            }
        """.trimIndent()
        val p = IpuzParser.parse(ipuz)
        assertTrue(p.cells[0].isBlock)
        assertEquals('A', p.cells[1].solution)
    }

    @Test
    fun rejectsNonCrosswordKindsAndBadGrids() {
        assertThrows(PuzFormatException::class.java) {
            IpuzParser.parse("""{"kind": ["http://ipuz.org/sudoku#1"]}""")
        }
        assertThrows(PuzFormatException::class.java) {
            IpuzParser.parse("""not json at all""")
        }
        // Barred grids (e.g. squares.io variety cryptics) would render as
        // nonsense if treated as block grids.
        assertThrows(PuzFormatException::class.java) {
            IpuzParser.parse(
                basic.replace(
                    "http://ipuz.org/crossword#1",
                    "https://squares.io/crossword-kind/barred-diagramless/no-reflow",
                ),
            )
        }
        // Declared numbering that contradicts the grid shape.
        assertThrows(PuzFormatException::class.java) {
            IpuzParser.parse(
                """
                {
                  "kind": ["http://ipuz.org/crossword#1"],
                  "dimensions": {"width": 2, "height": 1},
                  "puzzle": [[7, 0]],
                  "solution": [["A", "B"]]
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun formatDetectionRoutesJsonToIpuz() {
        val fromIpuz = PuzzleFormats.parse(basic.toByteArray())
        assertEquals("Test Ipuz", fromIpuz.title)
        // Leading whitespace and a UTF-8 BOM are still recognized as JSON.
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val withBom = PuzzleFormats.parse(bom + "\n  $basic".toByteArray())
        assertEquals("Test Ipuz", withBom.title)
        // Binary garbage falls through to the .puz parser and its errors.
        assertThrows(PuzFormatException::class.java) {
            PuzzleFormats.parse(ByteArray(100) { 7 })
        }
    }
}
