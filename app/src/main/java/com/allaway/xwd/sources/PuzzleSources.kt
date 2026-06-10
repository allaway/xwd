package com.allaway.xwd.sources

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * How a source's newest puzzle is located.
 */
sealed interface Fetch {
    /** Puzzle URLs follow a date pattern (one file per publication day). */
    data class Dated(
        val publishedOn: Set<DayOfWeek>,
        val urlFor: (LocalDate) -> String,
    ) : Fetch {
        /**
         * Candidate dates to try when fetching the latest puzzle, newest first.
         * Includes a couple of days ahead because some feeds post early.
         */
        fun candidateDates(today: LocalDate = LocalDate.now(), lookBackDays: Int = 28): List<LocalDate> =
            (-2..lookBackDays).map { today.minusDays(it.toLong()) }
                .filter { it.dayOfWeek in publishedOn }
    }

    /** The newest .puz link is discovered by scanning an HTML page. */
    data class LatestFromPage(
        val pageUrl: String,
        /** Regex whose group 1 captures the .puz href. */
        val linkPattern: Regex,
        /** Prepended when the captured href is relative. */
        val baseUrl: String,
    ) : Fetch
}

/**
 * A feed of real, human-constructed puzzles.
 *
 * Only sources whose rights holders themselves distribute the puzzle files
 * free of charge to the general public are included; [licenseBasis] records
 * the basis on which non-commercial solving use rests. Commercial syndicated
 * feeds (NYT, WSJ, Universal, LAT, ...) are deliberately absent: they are
 * copyrighted works with no license for third-party redistribution or reuse,
 * even where third-party mirrors of them exist.
 */
data class PuzzleSource(
    val id: String,
    val name: String,
    val attribution: String,
    val licenseBasis: String,
    val fetch: Fetch,
)

object PuzzleSources {

    private val YYMMDD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")

    val all: List<PuzzleSource> = listOf(
        PuzzleSource(
            id = "jonesin",
            name = "Jonesin’",
            attribution = "Jonesin’ Crosswords by Matt Jones, published weekly.",
            licenseBasis = "Self-syndicated by the author, who distributes the .puz file " +
                "free to the public each week (public mailing list and long-standing " +
                "community mirrors). Free for personal, non-commercial solving.",
            fetch = Fetch.Dated(
                publishedOn = setOf(DayOfWeek.THURSDAY),
                urlFor = { d -> "https://herbach.dnsalias.com/Jonesin/jz${YYMMDD.format(d)}.puz" },
            ),
        ),
        PuzzleSource(
            id = "beq",
            name = "BEQ",
            attribution = "Brendan Emmett Quigley, new puzzles Mondays and Thursdays.",
            licenseBasis = "Published free by the constructor on his own website " +
                "(brendanemmettquigley.com), supported by donations. Free for " +
                "personal, non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://www.brendanemmettquigley.com/",
                linkPattern = Regex("href=\"((?:https?://[^\"]+|/files/[^\"]+)\\.puz)\""),
                baseUrl = "https://www.brendanemmettquigley.com",
            ),
        ),
    )

    /** Sources with a per-date archive (offered in the date-picker dialog). */
    val dated: List<PuzzleSource> = all.filter { it.fetch is Fetch.Dated }

    fun byId(id: String): PuzzleSource? = all.firstOrNull { it.id == id }
}
