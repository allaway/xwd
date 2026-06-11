package com.allaway.xwd.puz

import com.allaway.xwd.sources.Fetch
import com.allaway.xwd.sources.PuzzleDownloader
import com.allaway.xwd.sources.PuzzleSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PuzzleSourcesTest {

    @Test
    fun everySourceDocumentsItsLicenseBasis() {
        PuzzleSources.all.forEach { source ->
            assertTrue(
                "${source.id} must document why non-commercial use is permitted",
                source.licenseBasis.contains("non-commercial"),
            )
        }
    }

    @Test
    fun noCommercialSyndicateFeeds() {
        val ids = PuzzleSources.all.map { it.id }
        listOf("wsj", "universal", "nyt", "lat", "newsday", "usatoday").forEach {
            assertTrue("commercial feed $it must not be a source", it !in ids)
        }
    }

    @Test
    fun jonesinCandidatesAreThursdaysNewestFirst() {
        val fetch = PuzzleSources.byId("jonesin")!!.fetch as Fetch.Dated
        val dates = fetch.candidateDates(today = LocalDate.of(2026, 6, 10))
        assertTrue(dates.isNotEmpty())
        assertTrue(dates.all { it.dayOfWeek == DayOfWeek.THURSDAY })
        assertEquals(LocalDate.of(2026, 6, 11), dates.first()) // posts early
        assertTrue(dates.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun extractsBeqPuzLinkFromHomepage() {
        val fetch = PuzzleSources.byId("beq")!!.fetch as Fetch.LatestFromPage
        val html = """
            <a href="/solve/?file=/files/1894ThemelessMonday.puz">play</a>
            <a href="/files/1894ThemelessMonday.puz">Across Lite</a>
        """.trimIndent()
        assertEquals(
            "https://www.brendanemmettquigley.com/files/1894ThemelessMonday.puz",
            PuzzleDownloader.extractLatestPuzUrl(html, fetch),
        )
        assertNull(PuzzleDownloader.extractLatestPuzUrl("<p>no puzzles here</p>", fetch))
    }

    @Test
    fun extractsDropboxPuzLinkWithEscapedQuery() {
        val fetch = PuzzleSources.byId("club72")!!.fetch as Fetch.LatestFromPage
        val html = """<a href="https://www.dropbox.com/scl/fi/abc/Puzzle1201Freestyle1122.pdf?rlkey=x&amp;raw=1">pdf</a>
            <a href="https://www.dropbox.com/scl/fi/xyz/Puzzle1201Freestyle1122.puz?rlkey=nxo&amp;st=10&amp;dl=1">puz</a>"""
        assertEquals(
            "https://www.dropbox.com/scl/fi/xyz/Puzzle1201Freestyle1122.puz?rlkey=nxo&st=10&dl=1",
            PuzzleDownloader.extractLatestPuzUrl(html, fetch),
        )
    }

    @Test
    fun extractsCrosshareDailyMiniId() {
        val fetch = PuzzleSources.byId("crosshare-mini")!!.fetch as Fetch.LatestFromPage
        val html = """"dailymini":{"title":"In Traffic","rating":{"r":1143.57,"d":62.19,"u":20611},""" +
            """"authorName":"Schmeel","authorId":"1ekGSxNMr1dt5hlnNWWHjqSJr2G2","id":"U4h00qNAL6jIuoBBudCe"}"""
        assertEquals(
            "https://crosshare.org/api/puz/U4h00qNAL6jIuoBBudCe",
            PuzzleDownloader.extractLatestPuzUrl(html, fetch),
        )
    }

    @Test
    fun jonesinArchiveDatesPageBackwardsAndStopAtArchiveStart() {
        val fetch = PuzzleSources.byId("jonesin")!!.fetch as Fetch.Dated
        val page = fetch.archiveDates(LocalDate.of(2026, 6, 12), 4)
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 11),
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 5, 21),
            ),
            page,
        )
        // The archive floor cuts the listing off rather than paging forever.
        val tail = fetch.archiveDates(LocalDate.of(2008, 1, 10), 10)
        assertEquals(listOf(LocalDate.of(2008, 1, 10), LocalDate.of(2008, 1, 3)), tail)
    }

    @Test
    fun extractsEveryPuzLinkOnAPageOnceEach() {
        val fetch = PuzzleSources.byId("beq")!!.fetch as Fetch.LatestFromPage
        val html = """
            <a href="/files/1895Marching.puz">Across Lite</a>
            <a href="/files/1895Marching.puz">again</a>
            <a href="/files/1894ThemelessMonday.puz">Across Lite</a>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://www.brendanemmettquigley.com/files/1895Marching.puz",
                "https://www.brendanemmettquigley.com/files/1894ThemelessMonday.puz",
            ),
            PuzzleDownloader.extractAllPuzUrls(html, fetch),
        )
    }

    @Test
    fun humanizesScrapedSlugsForCardTitles() {
        assertEquals("1894 Themeless Monday", PuzzleDownloader.humanizeSlug("1894ThemelessMonday"))
        assertEquals(
            "Puzzle 1201 Freestyle 1122",
            PuzzleDownloader.humanizeSlug("Puzzle1201Freestyle1122"),
        )
        assertEquals("tough as nails 412", PuzzleDownloader.humanizeSlug("tough-as-nails_412"))
    }

    @Test
    fun scrapedPuzzlesGetStableKeys() {
        assertEquals(
            "1894ThemelessMonday",
            PuzzleDownloader.puzUrlKey("https://www.brendanemmettquigley.com/files/1894ThemelessMonday.puz"),
        )
        assertEquals(
            "Puzzle1201Freestyle1122",
            PuzzleDownloader.puzUrlKey("https://www.dropbox.com/scl/fi/xyz/Puzzle1201Freestyle1122.puz?rlkey=nxo&dl=1"),
        )
        assertEquals(
            "U4h00qNAL6jIuoBBudCe",
            PuzzleDownloader.puzUrlKey("https://crosshare.org/api/puz/U4h00qNAL6jIuoBBudCe"),
        )
    }
}
