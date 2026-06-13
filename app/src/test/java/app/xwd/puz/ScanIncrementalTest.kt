package app.xwd.puz

import app.xwd.data.CatalogDao
import app.xwd.data.CatalogEntity
import app.xwd.data.CatalogRepository
import app.xwd.sources.PuzzleSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanIncrementalTest {

    /** In-memory CatalogDao; insert-ignore on existing ids, like Room's IGNORE. */
    private class FakeCatalogDao : CatalogDao {
        val rows = LinkedHashMap<String, CatalogEntity>()
        override fun observeAll(): Flow<List<CatalogEntity>> = flowOf(rows.values.toList())
        override suspend fun all(): List<CatalogEntity> = rows.values.toList()
        override suspend fun insertAll(rows: List<CatalogEntity>) {
            rows.forEach { this.rows.putIfAbsent(it.id, it) }
        }
        override suspend fun knownIds(ids: List<String>): List<String> = ids.filter { it in rows }
        override suspend fun oldestSortDate(sourceId: String): String? =
            rows.values.filter { it.sourceId == sourceId }.minOfOrNull { it.sortDate }
    }

    @Test
    fun firstScanWalksArchiveThenRescanEarlyStops() = runBlocking {
        val dao = FakeCatalogDao()
        // Real downloader: a dated source (Jonesin) lists offline, no network.
        val repo = CatalogRepository(dao)
        val jonesin = PuzzleSources.byId("jonesin")!!

        val firstAdded = repo.listEntireArchive(jonesin, maxPages = 5)
        assertTrue("first scan should catalogue several pages", firstAdded > 0)
        assertEquals(firstAdded, dao.all().size)

        // Nothing has changed, so a re-scan must add nothing and stop early
        // (not re-walk and re-list the whole archive).
        val secondAdded = repo.listEntireArchive(jonesin, maxPages = 5)
        assertEquals(0, secondAdded)
        assertEquals(firstAdded, dao.all().size)
    }
}
