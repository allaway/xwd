package app.xwd.model

/**
 * Finds cross-references to other clues inside a clue's text, e.g.
 * "see 32-Down" or "with 1, 5 and 9 Across". One reference phrase can name
 * several clue numbers that share a single trailing direction word, so each
 * number is returned individually (with the byte range it occupies in the
 * text, for tap targeting).
 *
 * Shared by the grid highlight (SolveViewModel.referencedCells) and the
 * tappable clue text (SolveScreen) so the two never drift apart.
 */
object ClueReferences {

    /**
     * A run of one or more clue numbers joined by list separators (comma,
     * slash, ampersand, hyphen, "and", whitespace), terminated by a single
     * direction word. Group 1 is the whole number run; group 2 the direction.
     *
     * The number run lets a single direction apply to every number before it,
     * which is how constructors write multi-entry answers ("1, 5 and 9 Across",
     * "17- and 25-Across", "12/13 Down"). A bare "32 Down" is just the simplest
     * case with one number.
     */
    private val PATTERN = Regex(
        """(\d+(?:[\s,/&-]+(?:and[\s,/&-]+)?\d+)*)[\s-]*(across|down)""",
        RegexOption.IGNORE_CASE,
    )

    private val NUMBER = Regex("""\d+""")

    /** One referenced clue: its [number], [direction], and span in the text. */
    data class Ref(val number: Int, val direction: Direction, val start: Int, val end: Int)

    fun find(text: String): List<Ref> {
        val refs = ArrayList<Ref>()
        for (match in PATTERN.findAll(text)) {
            val direction = if (match.groupValues[2].startsWith("a", ignoreCase = true)) {
                Direction.ACROSS
            } else {
                Direction.DOWN
            }
            val group = match.groups[1] ?: continue
            for (number in NUMBER.findAll(group.value)) {
                val n = number.value.toIntOrNull() ?: continue
                val start = group.range.first + number.range.first
                refs += Ref(n, direction, start, start + number.value.length)
            }
        }
        return refs
    }
}
