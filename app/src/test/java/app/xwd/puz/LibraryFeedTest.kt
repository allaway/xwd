package app.xwd.puz

import app.xwd.data.CatalogEntity
import app.xwd.data.PuzzleEntity
import app.xwd.ui.LibraryFeed
import app.xwd.ui.LibraryFilter
import app.xwd.ui.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeedTest {

    private fun catalogRow(sourceId: String, key: String, sortDate: String) = CatalogEntity(
        id = "$sourceId-$key",
        sourceId = sourceId,
        uniqueKey = key,
        title = key,
        date = null,
        url = "https://example.com/$key.puz",
        sortDate = sortDate,
        discoveredAt = 0,
    )

    private fun saved(sourceId: String, key: String, date: String) = PuzzleEntity(
        id = "$sourceId-$key",
        sourceId = sourceId,
        sourceName = sourceId,
        date = date,
        uniqueKey = key,
        title = key,
        author = "",
        puzzleJson = "{}",
        progress = "---",
        addedAt = 0,
    )

    private val catalog = listOf(
        catalogRow("beq", "newest", "2026-06-10"),
        catalogRow("club72", "middle", "2026-06-07"),
        catalogRow("beq", "oldest", "2026-06-04"),
    )

    @Test
    fun downloadingAPuzzleKeepsItsPositionAndId() {
        val before = LibraryFeed.build(emptyList(), catalog, emptySet(), filter = LibraryFilter.All)
        // "middle" gets downloaded: its saved row would naively sort by its
        // own (download-day) date and jump to the top. It must not.
        val after = LibraryFeed.build(
            listOf(saved("club72", "middle", date = "2026-06-10")),
            catalog,
            emptySet(),
            filter = LibraryFilter.All,
        )
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(1, after.indexOfFirst { it.id == "club72-middle" })
        assertTrue(after[1] is LibraryItem.Saved)
    }

    @Test
    fun downloadedFilterShowsOnlySavedItems()  {
        val feed = LibraryFeed.build(
            listOf(saved("club72", "middle", date = "2026-06-10")),
            catalog,
            emptySet(),
            filter = LibraryFilter.Downloaded,
        )
        assertEquals(listOf("club72-middle"), feed.map { it.id })
        assertTrue(feed.single() is LibraryItem.Saved)
    }

    @Test
    fun disabledSourcesAreHiddenButPhotoImportsNever() {
        val feed = LibraryFeed.build(
            listOf(saved("photo", "snap", date = "2026-06-09")),
            catalog,
            disabledSources = setOf("beq", "photo"),
            filter = LibraryFilter.All,
        )
        assertEquals(listOf("photo-snap", "club72-middle"), feed.map { it.id })
    }

    @Test
    fun sourceFilterShowsOnlyThatSourceSavedAndRemote() {
        val feed = LibraryFeed.build(
            listOf(saved("beq", "newest", date = "2026-06-10")),
            catalog,
            emptySet(),
            filter = LibraryFilter.Source("beq"),
        )
        assertEquals(listOf("beq-newest", "beq-oldest"), feed.map { it.id })
        assertTrue(feed[0] is LibraryItem.Saved)
        assertTrue(feed[1] is LibraryItem.Remote)
    }

    @Test
    fun savedPuzzlesOutsideTheCatalogSortByTheirOwnDate() {
        val feed = LibraryFeed.build(
            listOf(saved("jonesin", "2026-06-08", date = "2026-06-08")),
            catalog,
            emptySet(),
            filter = LibraryFilter.All,
        )
        assertEquals(
            listOf("beq-newest", "jonesin-2026-06-08", "club72-middle", "beq-oldest"),
            feed.map { it.id },
        )
    }
}
