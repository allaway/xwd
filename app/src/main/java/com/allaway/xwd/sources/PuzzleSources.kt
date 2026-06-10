package com.allaway.xwd.sources

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A feed of real, human-constructed puzzles published openly on the web.
 * All current sources serve Across Lite .puz files.
 */
data class PuzzleSource(
    val id: String,
    val name: String,
    val attribution: String,
    /** Days of the week on which this source publishes a puzzle. */
    val publishedOn: Set<DayOfWeek>,
    val urlFor: (LocalDate) -> String,
) {
    /**
     * Candidate dates to try when fetching the latest puzzle, newest first.
     * Includes a couple of days ahead because some feeds post early.
     */
    fun candidateDates(today: LocalDate = LocalDate.now(), lookBackDays: Int = 21): List<LocalDate> =
        (-2..lookBackDays).map { today.minusDays(it.toLong()) }
            .filter { it.dayOfWeek in publishedOn }
}

object PuzzleSources {

    private val YYMMDD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
    private val EVERY_DAY = DayOfWeek.entries.toSet()

    val all: List<PuzzleSource> = listOf(
        PuzzleSource(
            id = "wsj",
            name = "Wall Street Journal",
            attribution = "The Wall Street Journal daily crossword, edited by Mike Shenk.",
            publishedOn = EVERY_DAY - DayOfWeek.SUNDAY,
            urlFor = { d -> "https://herbach.dnsalias.com/wsj/wsj${YYMMDD.format(d)}.puz" },
        ),
        PuzzleSource(
            id = "universal",
            name = "Universal Crossword",
            attribution = "Universal Crossword (Andrews McMeel), edited by David Steinberg.",
            publishedOn = EVERY_DAY,
            urlFor = { d -> "https://herbach.dnsalias.com/uc/uc${YYMMDD.format(d)}.puz" },
        ),
        PuzzleSource(
            id = "jonesin",
            name = "Jonesin’",
            attribution = "Jonesin’ Crosswords by Matt Jones, published weekly.",
            publishedOn = setOf(DayOfWeek.THURSDAY),
            urlFor = { d -> "https://herbach.dnsalias.com/Jonesin/jz${YYMMDD.format(d)}.puz" },
        ),
    )

    fun byId(id: String): PuzzleSource? = all.firstOrNull { it.id == id }
}
