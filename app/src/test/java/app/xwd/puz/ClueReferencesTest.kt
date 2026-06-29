package app.xwd.puz

import app.xwd.model.ClueReferences
import app.xwd.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClueReferencesTest {

    private fun pairs(text: String): List<Pair<Int, Direction>> =
        ClueReferences.find(text).map { it.number to it.direction }

    @Test
    fun findsSimpleReference() {
        assertEquals(listOf(32 to Direction.DOWN), pairs("Songwriter of 32 Down"))
    }

    @Test
    fun findsHyphenatedReference() {
        assertEquals(listOf(16 to Direction.ACROSS), pairs("See 16-Across"))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals(listOf(5 to Direction.ACROSS), pairs("see 5 ACROSS"))
        assertEquals(listOf(5 to Direction.DOWN), pairs("see 5 down"))
    }

    @Test
    fun findsMultipleSeparateReferences() {
        assertEquals(
            listOf(1 to Direction.ACROSS, 2 to Direction.DOWN),
            pairs("1-Across and 2-Down together"),
        )
    }

    @Test
    fun sharesDirectionAcrossANumberList() {
        // A single trailing direction applies to every number before it.
        assertEquals(
            listOf(1 to Direction.ACROSS, 5 to Direction.ACROSS, 9 to Direction.ACROSS),
            pairs("With 1, 5 and 9 Across"),
        )
    }

    @Test
    fun handlesHyphenListWithAnd() {
        assertEquals(
            listOf(17 to Direction.ACROSS, 25 to Direction.ACROSS),
            pairs("17- and 25-Across"),
        )
    }

    @Test
    fun handlesSlashSeparatedMultiEntryAnswer() {
        assertEquals(
            listOf(12 to Direction.DOWN, 13 to Direction.DOWN),
            pairs("See 12/13 Down"),
        )
    }

    @Test
    fun handlesOxfordCommaList() {
        assertEquals(
            listOf(1 to Direction.DOWN, 2 to Direction.DOWN, 3 to Direction.DOWN, 4 to Direction.DOWN),
            pairs("1, 2, 3, and 4 Down"),
        )
    }

    @Test
    fun keepsDistinctDirectionsForMixedList() {
        assertEquals(
            listOf(1 to Direction.ACROSS, 2 to Direction.DOWN),
            pairs("1 Across and 2 Down"),
        )
    }

    @Test
    fun ignoresPlainNumbersWithoutDirection() {
        assertTrue(pairs("In 1969, a great year").isEmpty())
        assertTrue(pairs("Pay 5 dollars").isEmpty())
    }

    @Test
    fun doesNotTreatTrailingCommaPhraseAsReference() {
        // "across the board" is prose, not a clue reference.
        assertTrue(pairs("Reduced from 10 to 5, across the board").isEmpty())
    }

    @Test
    fun reportsAccurateSpansForEachNumber() {
        val text = "With 1, 5 and 9 Across"
        val refs = ClueReferences.find(text)
        assertEquals(listOf("1", "5", "9"), refs.map { text.substring(it.start, it.end) })
    }
}
