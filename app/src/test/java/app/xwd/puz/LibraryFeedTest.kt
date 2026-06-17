package app.xwd.puz

import app.xwd.data.CatalogEntity
import app.xwd.data.PuzzleEntity
import app.xwd.model.SizeClass
import app.xwd.ui.LibraryFeed
import app.xwd.ui.LibraryFilters
import app.xwd.ui.LibraryItem
import app.xwd.ui.PuzzleType
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

    /** [cells] becomes the progress length, which is how size class is derived. */
    private fun saved(sourceId: String, key: String, date: String, cells: Int = 3) = PuzzleEntity(
        id = "$sourceId-$key",
        sourceId = sourceId,
        sourceName = sourceId,
        date = date,
        uniqueKey = key,
        title = key,
        author = "",
        puzzleJson = "{}",
        progress = "-".repeat(cells),
        addedAt = 0,
    )

    private val catalog = listOf(
        catalogRow("beq", "newest", "2026-06-10"),
        catalogRow("club72", "middle", "2026-06-07"),
        catalogRow("beq", "oldest", "2026-06-04"),
    )

    @Test
    fun downloadingAPuzzleKeepsItsPositionAndId() {
        val before = LibraryFeed.build(emptyList(), catalog, emptySet(), emptySet(), LibraryFilters())
        // "middle" gets downloaded: its saved row would naively sort by its
        // own (download-day) date and jump to the top. It must not.
        val after = LibraryFeed.build(
            listOf(saved("club72", "middle", date = "2026-06-10")),
            catalog,
            emptySet(),
            emptySet(),
            LibraryFilters(),
        )
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(1, after.indexOfFirst { it.id == "club72-middle" })
        assertTrue(after[1] is LibraryItem.Saved)
    }

    @Test
    fun downloadedFilterShowsOnlySavedItems() {
        val feed = LibraryFeed.build(
            listOf(saved("club72", "middle", date = "2026-06-10")),
            catalog,
            emptySet(),
            emptySet(),
            LibraryFilters(downloadedOnly = true),
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
            crypticSourceIds = emptySet(),
            LibraryFilters(),
        )
        assertEquals(listOf("photo-snap", "club72-middle"), feed.map { it.id })
    }

    @Test
    fun sourceFilterShowsOnlyThatSourceSavedAndRemote() {
        val feed = LibraryFeed.build(
            listOf(saved("beq", "newest", date = "2026-06-10")),
            catalog,
            emptySet(),
            emptySet(),
            LibraryFilters(sourceId = "beq"),
        )
        assertEquals(listOf("beq-newest", "beq-oldest"), feed.map { it.id })
        assertTrue(feed[0] is LibraryItem.Saved)
        assertTrue(feed[1] is LibraryItem.Remote)
    }

    @Test
    fun sizeFilterMatchesDownloadedGridsAndHidesUnknownRemotes() {
        val saved = listOf(
            saved("beq", "newest", date = "2026-06-10", cells = 15 * 15), // Maxi
            saved("club72", "middle", date = "2026-06-07", cells = 5 * 5), // Mini
        )
        val maxi = LibraryFeed.build(saved, catalog, emptySet(), emptySet(), LibraryFilters(size = SizeClass.MAXI))
        assertEquals(listOf("beq-newest"), maxi.map { it.id })

        val mini = LibraryFeed.build(saved, catalog, emptySet(), emptySet(), LibraryFilters(size = SizeClass.MINI))
        assertEquals(listOf("club72-middle"), mini.map { it.id })

        // The un-downloaded "beq-oldest" remote has unknown size, so a size
        // filter excludes it entirely.
        assertTrue(maxi.none { it is LibraryItem.Remote })
    }

    @Test
    fun filtersCombine() {
        val saved = listOf(
            saved("beq", "newest", date = "2026-06-10", cells = 15 * 15), // Maxi, beq
            saved("club72", "middle", date = "2026-06-07", cells = 15 * 15), // Maxi, club72
        )
        val feed = LibraryFeed.build(
            saved, catalog, emptySet(), emptySet(),
            LibraryFilters(downloadedOnly = true, sourceId = "beq", size = SizeClass.MAXI),
        )
        assertEquals(listOf("beq-newest"), feed.map { it.id })
    }

    @Test
    fun savedPuzzlesOutsideTheCatalogSortByTheirOwnDate() {
        val feed = LibraryFeed.build(
            listOf(saved("jonesin", "2026-06-08", date = "2026-06-08")),
            catalog,
            emptySet(),
            emptySet(),
            LibraryFilters(),
        )
        assertEquals(
            listOf("beq-newest", "jonesin-2026-06-08", "club72-middle", "beq-oldest"),
            feed.map { it.id },
        )
    }

    @Test
    fun puzzleTypeFilterSplitsCrypticAndNormal() {
        // "beq" is treated as cryptic here; "club72" is normal.
        val cryptics = setOf("beq")
        val cryptic = LibraryFeed.build(
            emptyList(), catalog, emptySet(), cryptics,
            LibraryFilters(puzzleType = PuzzleType.CRYPTIC),
        )
        assertEquals(listOf("beq-newest", "beq-oldest"), cryptic.map { it.id })

        val normal = LibraryFeed.build(
            emptyList(), catalog, emptySet(), cryptics,
            LibraryFilters(puzzleType = PuzzleType.NORMAL),
        )
        assertEquals(listOf("club72-middle"), normal.map { it.id })
    }
}
