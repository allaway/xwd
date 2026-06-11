package app.xwd.ui

import app.xwd.data.CatalogEntity
import app.xwd.data.CatalogRepository.Companion.toCatalogEntry
import app.xwd.data.PuzzleEntity
import app.xwd.sources.PuzzleDownloader.CatalogEntry
import java.time.LocalDate

/** One row of the library feed: either on the device or available to download. */
sealed interface LibraryItem {
    val id: String
    val sortDate: LocalDate

    data class Saved(val entity: PuzzleEntity, override val sortDate: LocalDate) : LibraryItem {
        override val id: String get() = entity.id
    }

    data class Remote(val entry: CatalogEntry, override val sortDate: LocalDate) : LibraryItem {
        override val id: String get() = "${entry.sourceId}-${entry.uniqueKey}"
    }
}

/**
 * Builds the library feed from the persisted catalog and the saved puzzles.
 *
 * Ordering is anchored to the catalog's write-once sort dates: a puzzle that
 * is downloaded keeps its catalog position and id, so its card updates in
 * place instead of jumping elsewhere in the list.
 */
object LibraryFeed {

    fun build(
        saved: List<PuzzleEntity>,
        catalog: List<CatalogEntity>,
        disabledSources: Set<String>,
        onlyDownloaded: Boolean,
    ): List<LibraryItem> {
        val savedById = saved.associateBy { it.id }
        val catalogIds = catalog.mapTo(HashSet()) { it.id }

        val items = ArrayList<LibraryItem>(catalog.size + saved.size)
        for (row in catalog) {
            if (row.sourceId in disabledSources) continue
            val sortDate = parseDate(row.sortDate)
            val entity = savedById[row.id]
            items += if (entity != null) {
                LibraryItem.Saved(entity, sortDate)
            } else {
                LibraryItem.Remote(row.toCatalogEntry(), sortDate)
            }
        }
        // Saved puzzles with no catalog row (photo imports, archive-dialog
        // downloads, pre-catalog rows) sort by their own date.
        for (entity in saved) {
            if (entity.id in catalogIds) continue
            if (entity.sourceId != "photo" && entity.sourceId in disabledSources) continue
            items += LibraryItem.Saved(entity, parseDate(entity.date))
        }

        val visible = if (onlyDownloaded) items.filterIsInstance<LibraryItem.Saved>() else items
        return visible.sortedWith(compareByDescending<LibraryItem> { it.sortDate }.thenBy { it.id })
    }

    private fun parseDate(iso: String): LocalDate =
        try {
            LocalDate.parse(iso)
        } catch (_: Exception) {
            LocalDate.now()
        }
}
