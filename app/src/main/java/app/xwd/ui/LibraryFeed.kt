package app.xwd.ui

import app.xwd.data.CatalogEntity
import app.xwd.data.CatalogRepository.Companion.toCatalogEntry
import app.xwd.data.PuzzleEntity
import app.xwd.model.SizeClass
import app.xwd.sources.PuzzleDownloader.CatalogEntry
import java.time.LocalDate

/** One row of the library feed: either on the device or available to download. */
sealed interface LibraryItem {
    val id: String
    val sortDate: LocalDate
    val sourceId: String

    /** Grid size class, known only once a puzzle is on the device. */
    val sizeClass: SizeClass?

    data class Saved(val entity: PuzzleEntity, override val sortDate: LocalDate) : LibraryItem {
        override val id: String get() = entity.id
        override val sourceId: String get() = entity.sourceId
        override val sizeClass: SizeClass get() = SizeClass.forCellCount(entity.progress.length)
    }

    data class Remote(val entry: CatalogEntry, override val sortDate: LocalDate) : LibraryItem {
        override val id: String get() = "${entry.sourceId}-${entry.uniqueKey}"
        override val sourceId: String get() = entry.sourceId
        override val sizeClass: SizeClass? get() = null // unknown until downloaded
    }
}

/**
 * The library's view filters. All independent; an empty [LibraryFilters] shows
 * everything. [size] is known only for downloaded puzzles, so selecting a size
 * narrows to those (catalog entries of unknown size are hidden).
 */
data class LibraryFilters(
    val downloadedOnly: Boolean = false,
    val sourceId: String? = null,
    val size: SizeClass? = null,
) {
    val activeCount: Int
        get() = (if (downloadedOnly) 1 else 0) +
            (if (sourceId != null) 1 else 0) +
            (if (size != null) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
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
        filters: LibraryFilters,
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

        val visible = items.filter { item ->
            (!filters.downloadedOnly || item is LibraryItem.Saved) &&
                (filters.sourceId == null || item.sourceId == filters.sourceId) &&
                (filters.size == null || item.sizeClass == filters.size)
        }
        return visible.sortedWith(compareByDescending<LibraryItem> { it.sortDate }.thenBy { it.id })
    }

    private fun parseDate(iso: String): LocalDate =
        try {
            LocalDate.parse(iso)
        } catch (_: Exception) {
            LocalDate.now()
        }
}
