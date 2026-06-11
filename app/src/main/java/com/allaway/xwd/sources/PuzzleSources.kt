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
        /** Oldest date the feed's archive reaches back to. */
        val archiveStart: LocalDate = LocalDate.of(2008, 1, 1),
    ) : Fetch {
        /**
         * Candidate dates to try when fetching the latest puzzle, newest first.
         * Includes a couple of days ahead because some feeds post early.
         */
        fun candidateDates(today: LocalDate = LocalDate.now(), lookBackDays: Int = 28): List<LocalDate> =
            (-2..lookBackDays).map { today.minusDays(it.toLong()) }
                .filter { it.dayOfWeek in publishedOn }

        /**
         * Publication dates for browsing the archive, newest first, starting at
         * [newestInclusive] and never reaching past [archiveStart].
         */
        fun archiveDates(newestInclusive: LocalDate, count: Int): List<LocalDate> =
            generateSequence(newestInclusive) { it.minusDays(1) }
                .filter { it.dayOfWeek in publishedOn }
                .takeWhile { !it.isBefore(archiveStart) }
                .take(count)
                .toList()
    }

    /** The newest puzzle is discovered by scanning an HTML page. */
    data class LatestFromPage(
        val pageUrl: String,
        /** Regex whose group 1 captures the puzzle link or id, newest first on the page. */
        val linkPattern: Regex,
        /** Turns the (HTML-unescaped) capture into an absolute .puz URL. */
        val resolveUrl: (String) -> String = { it },
        /** URL of older archive pages (page 2, 3, ...), when the site is paginated. */
        val archivePageUrl: ((Int) -> String)? = null,
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

    /** Dropbox share links as used on WordPress blogs (Club 72, Tough as Nails). */
    private val DROPBOX_PUZ = Regex("href=\"(https://www\\.dropbox\\.com/[^\"]*\\.puz[^\"]*)\"")

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
                archiveStart = LocalDate.of(2008, 1, 3),
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
                resolveUrl = { href ->
                    if (href.startsWith("http")) href
                    else "https://www.brendanemmettquigley.com$href"
                },
            ),
        ),
        PuzzleSource(
            id = "club72",
            name = "Club 72",
            attribution = "Freestyle crosswords by Tim Croce, Tuesdays and Fridays.",
            licenseBasis = "Published free by the constructor on his own blog " +
                "(club72.wordpress.com) as .puz downloads. Free for personal, " +
                "non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://club72.wordpress.com/",
                linkPattern = DROPBOX_PUZ,
                archivePageUrl = { n -> "https://club72.wordpress.com/page/$n/" },
            ),
        ),
        PuzzleSource(
            id = "toughasnails",
            name = "Tough as Nails",
            attribution = "Hard themeless crosswords by Stella Zawistowski.",
            licenseBasis = "Published free by the constructor on her own site " +
                "(toughasnails.net) as .puz downloads. Free for personal, " +
                "non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://toughasnails.net/",
                linkPattern = DROPBOX_PUZ,
                archivePageUrl = { n -> "https://toughasnails.net/page/$n/" },
            ),
        ),
        PuzzleSource(
            id = "crosshare-mini",
            name = "Crosshare Daily Mini",
            attribution = "Community-constructed daily mini from crosshare.org.",
            licenseBasis = "Constructors publish on Crosshare, a free, open-source, " +
                "donation-funded platform, expressly for free public solving; the " +
                "platform itself provides the .puz export endpoint. Free for " +
                "personal, non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://crosshare.org/",
                linkPattern = Regex("\"dailymini\":.*?\"id\":\"([A-Za-z0-9]+)\""),
                resolveUrl = { id -> "https://crosshare.org/api/puz/$id" },
            ),
        ),
    )

    /** Sources with a per-date archive (offered in the date-picker dialog). */
    val dated: List<PuzzleSource> = all.filter { it.fetch is Fetch.Dated }

    fun byId(id: String): PuzzleSource? = all.firstOrNull { it.id == id }
}
