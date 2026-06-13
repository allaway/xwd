package app.xwd.puz

import app.xwd.data.BulkDownload
import app.xwd.data.CatalogEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BulkDownloadTest {

    private fun row(id: String, source: String, sortDate: String) = CatalogEntity(
        id = id,
        sourceId = source,
        uniqueKey = id.substringAfter("$source-"),
        title = id,
        date = sortDate,
        url = "https://example.com/$id.puz",
        sortDate = sortDate,
        discoveredAt = 0,
    )

    private val catalog = listOf(
        row("jonesin-2026-06-11", "jonesin", "2026-06-11"),
        row("jonesin-2026-06-04", "jonesin", "2026-06-04"),
        row("beq-1895", "beq", "2026-06-10"),
        row("club72-x", "club72", "2026-06-09"),
    )

    @Test
    fun pendingExcludesDownloadedAndDisabledNewestFirst() {
        val pending = BulkDownload.pending(
            catalog = catalog,
            savedIds = setOf("beq-1895"), // already downloaded
            disabledSources = setOf("club72"), // turned off
        )
        assertEquals(
            listOf("jonesin-2026-06-11", "jonesin-2026-06-04"),
            pending.map { it.id },
        )
    }

    @Test
    fun pendingIsEmptyWhenEverythingDownloaded() {
        val pending = BulkDownload.pending(
            catalog = catalog,
            savedIds = catalog.mapTo(HashSet()) { it.id },
            disabledSources = emptySet(),
        )
        assertEquals(emptyList<String>(), pending.map { it.id })
    }

    @Test
    fun pendingCountsEverythingWhenNothingSavedOrDisabled() {
        val pending = BulkDownload.pending(catalog, emptySet(), emptySet())
        assertEquals(4, pending.size)
        // Newest first.
        assertEquals("jonesin-2026-06-11", pending.first().id)
    }
}
