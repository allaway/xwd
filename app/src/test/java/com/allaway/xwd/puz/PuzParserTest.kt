package com.allaway.xwd.puz

import com.allaway.xwd.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Builds a minimal-but-valid .puz byte stream in memory so tests don't
 * depend on (copyrighted) downloaded puzzle files.
 */
private fun buildPuz(
    width: Int,
    height: Int,
    solution: String, // row-major, '.' for blocks
    clues: List<String>,
    title: String = "Test Puzzle",
    author: String = "Test Author",
    copyright: String = "© nobody",
    notes: String = "",
    scrambled: Boolean = false,
    gext: ByteArray? = null,
): ByteArray {
    val charset = Charset.forName("windows-1252")
    val out = ByteArrayOutputStream()
    val header = ByteArray(0x34)
    "ACROSS&DOWN".toByteArray(charset).copyInto(header, 0x02)
    "1.3".toByteArray(charset).copyInto(header, 0x18)
    header[0x2C] = width.toByte()
    header[0x2D] = height.toByte()
    header[0x2E] = (clues.size and 0xFF).toByte()
    header[0x2F] = ((clues.size shr 8) and 0xFF).toByte()
    header[0x30] = 1 // bitmask
    if (scrambled) header[0x32] = 4
    out.write(header)
    out.write(solution.toByteArray(charset))
    out.write(solution.map { if (it == '.') '.' else '-' }.joinToString("").toByteArray(charset))
    for (s in listOf(title, author, copyright) + clues + listOf(notes)) {
        out.write(s.toByteArray(charset))
        out.write(0)
    }
    if (gext != null) {
        out.write("GEXT".toByteArray(charset))
        out.write(byteArrayOf((gext.size and 0xFF).toByte(), ((gext.size shr 8) and 0xFF).toByte()))
        out.write(byteArrayOf(0, 0)) // checksum, unchecked by parser
        out.write(gext)
        out.write(0)
    }
    return out.toByteArray()
}

class PuzParserTest {

    // 3x3 with one block:
    //   C A B
    //   A G O
    //   . E N
    private val solution = "CABAGO.EN"
    private val clues = listOf(
        "1A: Taxi", // 1 Across CAB
        "1D: Dietary no-no for some", // 1 Down CA
        "2D: Past", // 2 Down AGE... wait, A-G-E? grid col1: A,G,E -> AGE
        "3D: Wager", // 3 Down: B,O,N -> BON? whatever, text doesn't matter
        "4A: Sock punch", // 4 Across AGO
        "5A: Termination", // 5 Across EN
    )

    @Test
    fun parsesHeaderAndMetadata() {
        val puzzle = PuzParser.parse(buildPuz(3, 3, solution, clues))
        assertEquals("Test Puzzle", puzzle.title)
        assertEquals("Test Author", puzzle.author)
        assertEquals(3, puzzle.width)
        assertEquals(3, puzzle.height)
        assertFalse(puzzle.scrambled)
        assertEquals(8, puzzle.whiteCellCount)
    }

    @Test
    fun assignsNumbersLikeAcrossLite() {
        val puzzle = PuzParser.parse(buildPuz(3, 3, solution, clues))
        assertEquals(1, puzzle.cellAt(0, 0).number)
        assertEquals(2, puzzle.cellAt(0, 1).number)
        assertEquals(3, puzzle.cellAt(0, 2).number)
        assertEquals(4, puzzle.cellAt(1, 0).number)
        assertEquals(5, puzzle.cellAt(2, 1).number)
        assertTrue(puzzle.cellAt(2, 0).isBlock)
    }

    @Test
    fun assignsCluesInPuzOrder() {
        val puzzle = PuzParser.parse(buildPuz(3, 3, solution, clues))
        assertEquals(6, puzzle.clues.size)
        val oneAcross = puzzle.clues.first { it.number == 1 && it.direction == Direction.ACROSS }
        assertEquals("1A: Taxi", oneAcross.text)
        assertEquals(listOf(0, 1, 2), oneAcross.cells)
        val oneDown = puzzle.clues.first { it.number == 1 && it.direction == Direction.DOWN }
        assertEquals("1D: Dietary no-no for some", oneDown.text)
        assertEquals(listOf(0, 3), oneDown.cells)
        val fiveAcross = puzzle.clues.first { it.number == 5 }
        assertEquals(Direction.ACROSS, fiveAcross.direction)
        assertEquals(listOf(7, 8), fiveAcross.cells)
    }

    @Test
    fun solutionLettersAreUppercased() {
        val puzzle = PuzParser.parse(buildPuz(3, 3, "cabago.en", clues))
        assertEquals('C', puzzle.cellAt(0, 0).solution)
        assertNull(puzzle.cellAt(2, 0).solution)
    }

    @Test
    fun readsScrambledFlag() {
        val puzzle = PuzParser.parse(buildPuz(3, 3, solution, clues, scrambled = true))
        assertTrue(puzzle.scrambled)
    }

    @Test
    fun readsGextCircles() {
        val gext = ByteArray(9)
        gext[4] = 0x80.toByte()
        val puzzle = PuzParser.parse(buildPuz(3, 3, solution, clues, gext = gext))
        assertTrue(puzzle.cellAt(1, 1).circled)
        assertFalse(puzzle.cellAt(0, 0).circled)
    }

    @Test(expected = PuzFormatException::class)
    fun rejectsGarbage() {
        PuzParser.parse(ByteArray(100))
    }

    @Test(expected = PuzFormatException::class)
    fun rejectsClueCountMismatch() {
        PuzParser.parse(buildPuz(3, 3, solution, clues.dropLast(1)))
    }

    @Test
    fun clueNavigationWrapsAround() {
        val puzzle = PuzParser.parse(buildPuz(3, 3, solution, clues))
        val first = puzzle.cluesFor(Direction.ACROSS).first()
        val last = puzzle.cluesFor(Direction.DOWN).last()
        assertEquals(first, puzzle.nextClue(last))
        assertEquals(last, puzzle.previousClue(first))
    }
}
