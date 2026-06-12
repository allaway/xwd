package app.xwd.puz

import app.xwd.sources.Fetch
import app.xwd.sources.PuzzleDownloader
import app.xwd.sources.PuzzleSources
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
    fun extractsCrosshareDailyMiniIdsFromListing() {
        val fetch = PuzzleSources.byId("crosshare-mini")!!.fetch as Fetch.LatestFromPage
        // /dailyminis pages link each mini twice (thumbnail and title).
        val html = """
            <a href="/crosswords/r1vpcu5HHRRnLUIPHBWH/357-justice">357: Justice</a>
            <a href="/crosswords/r1vpcu5HHRRnLUIPHBWH/357-justice">again</a>
            <a href="/crosswords/U4h00qNAL6jIuoBBudCe/in-traffic">In Traffic</a>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://crosshare.org/api/puz/r1vpcu5HHRRnLUIPHBWH",
                "https://crosshare.org/api/puz/U4h00qNAL6jIuoBBudCe",
            ),
            PuzzleDownloader.extractAllPuzUrls(html, fetch),
        )
    }

    @Test
    fun crosshareArchivePagesAreMonthsBackwardsFromNow() {
        val fetch = PuzzleSources.byId("crosshare-mini")!!.fetch as Fetch.LatestFromPage
        val thisMonth = LocalDate.now()
        assertEquals(
            "https://crosshare.org/dailyminis/${thisMonth.year}/${thisMonth.monthValue}",
            fetch.archivePageUrl!!(1),
        )
        val lastMonth = thisMonth.minusMonths(1)
        assertEquals(
            "https://crosshare.org/dailyminis/${lastMonth.year}/${lastMonth.monthValue}",
            fetch.archivePageUrl!!(2),
        )
    }

    @Test
    fun extractsPuzzlePitZipsFromFeed() {
        val fetch = PuzzleSources.byId("puzzlepit")!!.fetch as Fetch.LatestFromPage
        // The RSS feed carries each post's full HTML: a Crosshare embed link
        // (ignored) and the self-hosted ZIP linked twice.
        val html = """
            <a href="https://crosshare.org/embed/8I6Nbi2vMG5s3MmP1c6a/tMB58LHUn9cQvjglVzfkzcNhgQX2">solve</a>
            <a href="https://thepuzzlepit.com/wp-content/uploads/2026/06/themed-wednesday-18-_om-nom-nom_.zip">puz</a>
            <a href="https://thepuzzlepit.com/wp-content/uploads/2026/06/themed-wednesday-18-_om-nom-nom_.zip">again</a>
            <a href="https://thepuzzlepit.com/wp-content/uploads/2026/06/metal-monday-55-themeless.zip">puz</a>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://thepuzzlepit.com/wp-content/uploads/2026/06/themed-wednesday-18-_om-nom-nom_.zip",
                "https://thepuzzlepit.com/wp-content/uploads/2026/06/metal-monday-55-themeless.zip",
            ),
            PuzzleDownloader.extractAllPuzUrls(html, fetch),
        )
    }

    @Test
    fun extractsPrivateEyePuzLink() {
        val fetch = PuzzleSources.byId("privateeye")!!.fetch as Fetch.LatestFromPage
        val html = """<a href="https://www.private-eye.co.uk/pictures/crossword/download/831.puz">Download</a>"""
        assertEquals(
            "https://www.private-eye.co.uk/pictures/crossword/download/831.puz",
            PuzzleDownloader.extractLatestPuzUrl(html, fetch),
        )
    }

    @Test
    fun extractsBewilderinglyDropboxLink() {
        val fetch = PuzzleSources.byId("bewilderingly")!!.fetch as Fetch.LatestFromPage
        val html = """<a href="https://www.dropbox.com/scl/fi/r2sgbxdbvyibz3gnkhlzj/060126-freestyle-24.pdf?rlkey=zca&amp;dl=1">pdf</a>
            <a href="https://www.dropbox.com/scl/fi/2h909ors96s18o6no5ynk/060126-freestyle-24.puz?rlkey=xj0&amp;dl=1">puz</a>"""
        assertEquals(
            "https://www.dropbox.com/scl/fi/2h909ors96s18o6no5ynk/060126-freestyle-24.puz?rlkey=xj0&dl=1",
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
    fun genericPatternMatchesPuzAndIpuzButNotLookalikes() {
        val fetch = PuzzleSources.byId("jkl")!!.fetch as Fetch.LatestFromPage
        val html = """
            <a href="https://jklcrosswords.com/files/puzzle-41.ipuz">ipuz</a>
            <a href="/files/puzzle-42.puz?v=2">puz</a>
            <a href="/files/puzzle-41.puz">same puzzle, other format</a>
            <a href="https://crossword.puzzlesociety.com/daily">not a file</a>
        """.trimIndent()
        // The .puz twin of puzzle-41 dedupes away (same key as the .ipuz).
        assertEquals(
            listOf(
                "https://jklcrosswords.com/files/puzzle-41.ipuz",
                "https://jklcrosswords.com/files/puzzle-42.puz?v=2",
            ),
            PuzzleDownloader.extractAllPuzUrls(html, fetch),
        )
    }

    @Test
    fun newSourcesResolveRelativeLinks() {
        val fetch = PuzzleSources.byId("mmmm")!!.fetch as Fetch.LatestFromPage
        val html = """<a href="/wp-content/uploads/2026/06/meta6.puz">Across Lite</a>"""
        assertEquals(
            "https://pmxwords.com/wp-content/uploads/2026/06/meta6.puz",
            PuzzleDownloader.extractLatestPuzUrl(html, fetch),
        )
    }

    @Test
    fun ipuzUrlsGetStableKeysToo() {
        assertEquals(
            "puzzle-41",
            PuzzleDownloader.puzUrlKey("https://jklcrosswords.com/files/puzzle-41.ipuz"),
        )
    }

    @Test
    fun dropboxPreviewLinksAreRewrittenToDirectDownloads() {
        val fetch = PuzzleSources.byId("jkl")!!.fetch as Fetch.LatestFromPage
        // JKL links the dl=0 (HTML preview) form of its Dropbox files.
        val html = """<a href="https://www.dropbox.com/s/uys34dnpuigm8iq/Letter%20for%20Letter.puz?dl=0">puz</a>"""
        assertEquals(
            "https://www.dropbox.com/s/uys34dnpuigm8iq/Letter%20for%20Letter.puz?dl=1",
            PuzzleDownloader.extractLatestPuzUrl(html, fetch),
        )
        // A Dropbox link with no dl parameter at all also gets one.
        assertEquals(
            "https://www.dropbox.com/s/abc/foo.puz?rlkey=z&dl=1",
            PuzzleDownloader.directDownloadUrl("https://www.dropbox.com/s/abc/foo.puz?rlkey=z"),
        )
        // Non-Dropbox URLs pass through untouched.
        assertEquals(
            "https://jklcrosswords.com/files/puzzle-41.ipuz",
            PuzzleDownloader.directDownloadUrl("https://jklcrosswords.com/files/puzzle-41.ipuz"),
        )
    }

    @Test
    fun percentEncodedFileNamesDecodeInKeysAndTitles() {
        val key = PuzzleDownloader.puzUrlKey(
            "https://www.dropbox.com/s/uys34dnpuigm8iq/Letter%20for%20Letter.puz?dl=1",
        )
        assertEquals("Letter for Letter", key)
        assertEquals("Letter for Letter", PuzzleDownloader.humanizeSlug(key))
    }

    @Test
    fun crosshareEntriesAreTitledBySourceNotOpaqueId() {
        assertEquals(
            "Crosshare Daily Mini",
            PuzzleDownloader.catalogTitle(
                "Crosshare Daily Mini",
                "https://crosshare.org/api/puz/U4h00qNAL6jIuoBBudCe",
                "U4h00qNAL6jIuoBBudCe",
            ),
        )
        // Ordinary scraped files keep their humanized slug titles.
        assertEquals(
            "1894 Themeless Monday",
            PuzzleDownloader.catalogTitle(
                "BEQ",
                "https://www.brendanemmettquigley.com/files/1894ThemelessMonday.puz",
                "1894ThemelessMonday",
            ),
        )
    }

    @Test
    fun zippedPuzzlesGetKeysWithoutTheZipExtension() {
        assertEquals(
            "metal-monday-55-themeless",
            PuzzleDownloader.puzUrlKey("https://thepuzzlepit.com/wp-content/uploads/2026/06/metal-monday-55-themeless.zip"),
        )
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
