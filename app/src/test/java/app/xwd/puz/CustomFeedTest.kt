package app.xwd.puz

import app.xwd.sources.CustomFeed
import app.xwd.sources.Fetch
import app.xwd.sources.PuzzleDownloader
import app.xwd.sources.PuzzleSources
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFeedTest {

    @Test
    fun customFeedsRoundTripThroughJson() {
        val feeds = listOf(
            CustomFeed("custom-1", "My Blog", "https://example.com/puzzles/"),
            CustomFeed("custom-2", "Another", "https://two.example/"),
        )
        val json = Json.encodeToString(feeds)
        assertEquals(feeds, Json.decodeFromString<List<CustomFeed>>(json))
    }

    @Test
    fun fromCustomScrapesPuzAndIpuzLinksResolvingRelativeUrls() {
        val source = PuzzleSources.fromCustom(
            CustomFeed("custom-9", "My Blog", "https://example.com/puzzles/"),
        )
        assertTrue(source.id.startsWith(PuzzleSources.CUSTOM_PREFIX))
        assertEquals("My Blog", source.name)
        val fetch = source.fetch as Fetch.LatestFromPage
        val html = """
            <a href="/files/mon.puz">puz</a>
            <a href="https://cdn.example.com/tue.ipuz">ipuz</a>
            <a href="https://example.com/not-a-puzzle">nope</a>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://example.com/files/mon.puz",
                "https://cdn.example.com/tue.ipuz",
            ),
            PuzzleDownloader.extractAllPuzUrls(html, fetch),
        )
    }

    @Test
    fun customFeedDocumentsNonCommercialBasisLikeBuiltInSources() {
        val source = PuzzleSources.fromCustom(CustomFeed("custom-3", "X", "https://x.example/"))
        assertTrue(source.licenseBasis.contains("non-commercial"))
    }
}
