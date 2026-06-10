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
    fun scrapedPuzzlesGetStableKeys() {
        assertEquals(
            "1894ThemelessMonday",
            PuzzleDownloader.puzUrlKey("https://www.brendanemmettquigley.com/files/1894ThemelessMonday.puz"),
        )
    }
}
