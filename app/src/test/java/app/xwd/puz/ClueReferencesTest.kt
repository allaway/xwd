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

    @Test
    fun handlesAbbreviatedDirections() {
        // British and cryptic puzzles abbreviate across/down as ac/dn.
        assertEquals(listOf(5 to Direction.ACROSS), pairs("See 5ac"))
        assertEquals(listOf(12 to Direction.DOWN), pairs("Cf. 12dn"))
        assertEquals(listOf(12 to Direction.ACROSS), pairs("12ac"))
        assertEquals(
            listOf(9 to Direction.DOWN, 14 to Direction.ACROSS),
            pairs("9dn and 14ac"),
        )
    }

    @Test
    fun abbreviationDoesNotMatchInsideWords() {
        // "ac" must not fire inside "acre" / "across the" prose fragments.
        assertTrue(pairs("A field of 5 acres").isEmpty())
    }

    @Test
    fun handlesOrLists() {
        assertEquals(
            listOf(17 to Direction.ACROSS, 25 to Direction.ACROSS),
            pairs("17- or 25-Across"),
        )
        assertEquals(
            listOf(5 to Direction.DOWN, 6 to Direction.DOWN),
            pairs("5 or 6 Down"),
        )
    }

    @Test
    fun handlesAmpersandLists() {
        assertEquals(
            listOf(1 to Direction.ACROSS, 5 to Direction.ACROSS),
            pairs("1 & 5 Across"),
        )
    }

    @Test
    fun handlesHyphenatedNumberRuns() {
        assertEquals(
            listOf(4 to Direction.DOWN, 5 to Direction.DOWN, 6 to Direction.DOWN),
            pairs("see 4-, 5- and 6-Down"),
        )
    }

    @Test
    fun handlesEnDashRange() {
        assertEquals(
            listOf(17 to Direction.ACROSS, 25 to Direction.ACROSS),
            pairs("17–25 Across"),
        )
    }

    @Test
    fun guardsAgainstAcrossTheBoardProse() {
        assertTrue(pairs("10 across the board").isEmpty())
        assertTrue(pairs("knocked 5 down to the ground").isEmpty())
        assertTrue(pairs("go 9 down from there").isEmpty())
    }

    @Test
    fun ignoresOrdinals() {
        assertTrue(pairs("the 16th down the list").isEmpty())
    }

    @Test
    fun handlesComplexMixedReference() {
        assertEquals(
            listOf(
                1 to Direction.ACROSS,
                16 to Direction.DOWN,
                38 to Direction.DOWN,
                54 to Direction.DOWN,
            ),
            pairs("With 1-Across, 16-, 38- and 54-Down"),
        )
    }
}
