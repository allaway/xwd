package app.xwd.data

import app.xwd.sources.Fetch
import app.xwd.sources.PuzzleDownloader
import app.xwd.sources.PuzzleDownloader.CatalogEntry
import app.xwd.sources.PuzzleSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Maintains the persistent catalog of puzzles available for download.
 *
 * Rows are insert-only ([CatalogDao.insertAll] ignores conflicts), so a
 * puzzle's [CatalogEntity.sortDate] — and with it its position in the
 * library feed — is fixed the first time it is seen.
 */
class CatalogRepository(
    private val dao: CatalogDao,
    private val downloader: PuzzleDownloader = PuzzleDownloader(),
) {

    fun observeAll(): Flow<List<CatalogEntity>> = dao.observeAll()

    /**
     * List the newest puzzles of [source] (recent dates, or page 1 of a
     * scraped feed) and record any not yet in the catalog. This is the
     * "did anything new come out?" check run at launch and from the
     * background refresher. Returns the entries that were newly added.
     */
    suspend fun refreshNewest(source: PuzzleSource): List<CatalogEntry> =
        when (source.fetch) {
            is Fetch.Dated -> recordDated(
                downloader.listDated(source, LocalDate.now().plusDays(2), PAGE_SIZE),
            )
            is Fetch.LatestFromPage -> {
                val entries = downloader.listScraped(source, page = 1)
                // Newly published scraped puzzles sit at the top of page 1;
                // date them from today downward.
                recordScraped(source, entries, newestFirstFrom = LocalDate.now())
            }
        }

    /**
     * Walk a source's archive from newest to oldest, recording puzzles in the
     * catalog, and stop as soon as a page contains nothing new — i.e. once we
     * reach the part of the archive already catalogued. Returns the number of
     * rows actually added. [onProgress] reports the running total. This makes
     * the first scan walk the whole archive but later re-scans only touch the
     * front, instead of re-fetching (and re-listing) everything every time.
     */
    suspend fun listEntireArchive(
        source: PuzzleSource,
        maxPages: Int = 400,
        onProgress: (added: Int) -> Unit = {},
    ): Int {
        var added = 0
        when (val fetch = source.fetch) {
            is Fetch.Dated -> {
                // Dated listing is computed offline, so this loop is cheap.
                var newestInclusive = LocalDate.now().plusDays(2)
                var page = 0
                while (page < maxPages) {
                    val entries = downloader.listDated(source, newestInclusive, PAGE_SIZE)
                    if (entries.isEmpty()) break // archive floor
                    val fresh = recordDated(entries)
                    added += fresh.size
                    onProgress(added)
                    if (fresh.isEmpty()) break // reached already-catalogued dates
                    newestInclusive = entries.last().date!!.minusDays(1)
                    page++
                }
            }
            is Fetch.LatestFromPage -> {
                val hadCatalog = dao.oldestSortDate(source.id) != null
                val frontFresh = recordScraped(
                    source, downloader.listScraped(source, page = 1), LocalDate.now(),
                ).size
                added += frontFresh
                onProgress(added)
                // Only page deeper on the first scan, or when the front page
                // turned up something new (older pages are otherwise known).
                if (fetch.archivePageUrl != null && (!hadCatalog || frontFresh > 0)) {
                    var page = 2
                    while (page < maxPages + 2) {
                        val entries = downloader.listScraped(source, page)
                        if (entries.isEmpty()) break // no more pages
                        val oldest = dao.oldestSortDate(source.id)?.let(LocalDate::parse) ?: LocalDate.now()
                        val fresh = recordScraped(source, entries, oldest.minusDays(SCRAPED_DAY_STEP))
                        added += fresh.size
                        onProgress(added)
                        if (fresh.isEmpty()) break // reached already-catalogued puzzles
                        page++
                    }
                }
            }
        }
        return added
    }

    /**
     * Extend the catalog one page further back into a dated source's
     * archive. Returns the number of puzzles listed; 0 means the archive
     * floor was reached.
     */
    suspend fun loadOlderDated(source: PuzzleSource): Int {
        val oldest = dao.oldestSortDate(source.id)?.let(LocalDate::parse)
        val newestInclusive = oldest?.minusDays(1) ?: LocalDate.now().plusDays(2)
        val entries = downloader.listDated(source, newestInclusive, PAGE_SIZE)
        recordDated(entries)
        return entries.size
    }

    /**
     * Extend the catalog with archive page [page] of a scraped source.
     * Returns the number of puzzles listed; 0 means there are no more pages.
     */
    suspend fun loadOlderScraped(source: PuzzleSource, page: Int): Int {
        val entries = downloader.listScraped(source, page)
        // Older pages hold puzzles older than everything known so far.
        val oldest = dao.oldestSortDate(source.id)?.let(LocalDate::parse) ?: LocalDate.now()
        recordScraped(source, entries, newestFirstFrom = oldest.minusDays(SCRAPED_DAY_STEP))
        return entries.size
    }

    private suspend fun recordDated(entries: List<CatalogEntry>): List<CatalogEntry> {
        if (entries.isEmpty()) return emptyList()
        val known = dao.knownIds(entries.map { it.catalogId }).toSet()
        val fresh = entries.filter { it.catalogId !in known }
        dao.insertAll(fresh.map { it.toEntity(sortDate = it.date!!) })
        return fresh
    }

    /**
     * Scraped feeds carry no publish dates, so new entries get approximate,
     * write-once sort dates: [SCRAPED_DAY_STEP]-day steps below
     * [newestFirstFrom], in page order, counting only entries not already
     * in the catalog. Returns the entries that were newly added.
     */
    private suspend fun recordScraped(
        source: PuzzleSource,
        entries: List<CatalogEntry>,
        newestFirstFrom: LocalDate,
    ): List<CatalogEntry> {
        if (entries.isEmpty()) return emptyList()
        val known = dao.knownIds(entries.map { it.catalogId }).toSet()
        val fresh = entries.filter { it.catalogId !in known }
        dao.insertAll(
            fresh.mapIndexed { i, entry ->
                entry.toEntity(sortDate = newestFirstFrom.minusDays(SCRAPED_DAY_STEP * i))
            },
        )
        return fresh
    }

    companion object {
        const val PAGE_SIZE = 10
        private const val SCRAPED_DAY_STEP = 3L

        private val CatalogEntry.catalogId: String get() = "$sourceId-$uniqueKey"

        private fun CatalogEntry.toEntity(sortDate: LocalDate) = CatalogEntity(
            id = catalogId,
            sourceId = sourceId,
            uniqueKey = uniqueKey,
            title = title,
            date = date?.toString(),
            url = url,
            sortDate = sortDate.toString(),
            discoveredAt = System.currentTimeMillis(),
        )

        fun CatalogEntity.toCatalogEntry() = CatalogEntry(
            sourceId = sourceId,
            uniqueKey = uniqueKey,
            title = title,
            date = date?.let(LocalDate::parse),
            url = url,
        )
    }
}
