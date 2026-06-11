package app.xwd.model

/**
 * Size classes for crossword grids, from the Crosshare-style daily mini up
 * to oversized grids that need panning to solve. The top two classes are
 * reserved for genuinely huge puzzles (Sunday-size and beyond).
 */
enum class SizeClass(val label: String) {
    MINI("Mini"),
    MIDI("Midi"),
    MAXI("Maxi"),
    SUPERMAXI("Supermaxi"),
    ULTRAMAXI("Ultramaxi");

    companion object {
        /**
         * Classify by total cell count, the one size measure available
         * without decoding the stored grid. Thresholds are the squares of
         * the customary max dimensions: 8 (mini), 13 (midi), 17 (maxi,
         * the standard 15x15 lives here), 22 (supermaxi, Sunday 21x21),
         * and anything bigger is ultramaxi.
         */
        fun forCellCount(cells: Int): SizeClass = when {
            cells <= 8 * 8 -> MINI
            cells <= 13 * 13 -> MIDI
            cells <= 17 * 17 -> MAXI
            cells <= 22 * 22 -> SUPERMAXI
            else -> ULTRAMAXI
        }

        fun forDimensions(width: Int, height: Int): SizeClass = forCellCount(width * height)
    }
}
