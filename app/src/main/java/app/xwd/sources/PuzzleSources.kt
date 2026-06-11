package app.xwd.sources

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
 * the basis on which non-commercial solving use rests. A commercial publisher
 * qualifies when it posts the files itself with no login (e.g. Private Eye).
 * Commercial syndicated feeds (NYT, WSJ, Universal, LAT, ...) are deliberately
 * absent: they are copyrighted works with no license for third-party
 * redistribution or reuse, even where third-party mirrors of them exist.
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

    /** Any direct .puz or .ipuz link, with an optional query string. */
    private val ANY_PUZ = Regex("href=\"([^\"]+\\.i?puz(?:[?#][^\"]*)?)\"")

    /** Resolves scraped hrefs (absolute, site-rooted, or relative) against a page URL. */
    private fun resolver(pageUrl: String): (String) -> String {
        val origin = Regex("^(https?://[^/]+)").find(pageUrl)?.groupValues?.get(1) ?: pageUrl
        return { href ->
            when {
                href.startsWith("http") -> href
                href.startsWith("/") -> origin + href
                else -> pageUrl.trimEnd('/') + "/" + href
            }
        }
    }

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
            id = "mmmm",
            name = "Muller Music Meta",
            attribution = "Muller Monthly Music Meta by Pete Muller, one meta crossword a month.",
            licenseBasis = "Published free by the constructor on his own site " +
                "(pmxwords.com), supported by donations and an optional leaderboard. " +
                "Free for personal, non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://pmxwords.com/",
                linkPattern = ANY_PUZ,
                resolveUrl = resolver("https://pmxwords.com/"),
                archivePageUrl = { n -> "https://pmxwords.com/page/$n/" },
            ),
        ),
        PuzzleSource(
            id = "squarepursuit",
            name = "Square Pursuit",
            attribution = "Crosswords by Steve Mossberg.",
            licenseBasis = "Published free by the constructor on his own blog " +
                "(squarepursuit.com) as .puz downloads. Free for personal, " +
                "non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://squarepursuit.com/",
                linkPattern = ANY_PUZ,
                resolveUrl = resolver("https://squarepursuit.com/"),
                archivePageUrl = { n -> "https://squarepursuit.com/page/$n/" },
            ),
        ),
        PuzzleSource(
            id = "jkl",
            name = "JKL Crosswords",
            attribution = "Crosswords by Jesse Lansner.",
            licenseBasis = "Published free by the constructor on his own site " +
                "(jklcrosswords.com) as .puz and .ipuz downloads. Free for " +
                "personal, non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://jklcrosswords.com/",
                linkPattern = ANY_PUZ,
                resolveUrl = resolver("https://jklcrosswords.com/"),
                // The site uses /index.php/ permalinks for its archive pages.
                archivePageUrl = { n -> "https://jklcrosswords.com/index.php/page/$n/" },
            ),
        ),
        PuzzleSource(
            id = "bewilderingly",
            name = "Bewilderingly",
            attribution = "Crosswords by Will Nediger, roughly every other Monday.",
            licenseBasis = "Published free by the constructor on his own blog " +
                "(blog.bewilderinglypuzzles.com) as .puz downloads, supported by " +
                "donations. Free for personal, non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://blog.bewilderinglypuzzles.com/",
                linkPattern = DROPBOX_PUZ,
                // Blogger's archive isn't numerically paginated, so only the
                // front page's posts are browsable.
            ),
        ),
        PuzzleSource(
            id = "puzzlepit",
            name = "The Puzzle Pit",
            attribution = "Themed and themeless crosswords, three times a week.",
            licenseBasis = "Published free by the constructor on their own site " +
                "(thepuzzlepit.com) as zipped .puz downloads. Free for personal, " +
                "non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                // The front page links only post permalinks; the RSS feed
                // carries the full post HTML including the file links.
                pageUrl = "https://thepuzzlepit.com/feed/",
                linkPattern = Regex("href=\"(https://thepuzzlepit\\.com/wp-content/[^\"]+\\.zip)\""),
                archivePageUrl = { n -> "https://thepuzzlepit.com/feed/?paged=$n" },
            ),
        ),
        PuzzleSource(
            id = "nevillefogarty",
            name = "Neville Fogarty",
            attribution = "Friday crosswords by Neville Fogarty (archive; last new puzzle 2022).",
            licenseBasis = "Published free by the constructor on his own blog " +
                "(nevillefogarty.wordpress.com) as .puz downloads. Free for " +
                "personal, non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://nevillefogarty.wordpress.com/",
                linkPattern = DROPBOX_PUZ,
                archivePageUrl = { n -> "https://nevillefogarty.wordpress.com/page/$n/" },
            ),
        ),
        PuzzleSource(
            id = "privateeye",
            name = "Private Eye",
            attribution = "Private Eye’s fortnightly cryptic by Cyclops.",
            licenseBasis = "The magazine itself posts each issue’s cryptic as a " +
                "free .puz download on private-eye.co.uk, no login or subscription " +
                "required. Free for personal, non-commercial solving.",
            fetch = Fetch.LatestFromPage(
                pageUrl = "https://www.private-eye.co.uk/crossword",
                linkPattern = ANY_PUZ,
                resolveUrl = resolver("https://www.private-eye.co.uk/crossword"),
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
                pageUrl = "https://crosshare.org/dailyminis",
                linkPattern = Regex("href=\"/crosswords/([A-Za-z0-9]+)"),
                resolveUrl = { id -> "https://crosshare.org/api/puz/$id" },
                // Page n of the archive is the month n-1 months back.
                archivePageUrl = { n ->
                    val month = LocalDate.now().minusMonths(n - 1L)
                    "https://crosshare.org/dailyminis/${month.year}/${month.monthValue}"
                },
            ),
        ),
    )

    /** Sources with a per-date archive (offered in the date-picker dialog). */
    val dated: List<PuzzleSource> = all.filter { it.fetch is Fetch.Dated }

    fun byId(id: String): PuzzleSource? = all.firstOrNull { it.id == id }
}
