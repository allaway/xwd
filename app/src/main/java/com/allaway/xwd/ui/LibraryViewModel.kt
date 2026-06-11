package com.allaway.xwd.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allaway.xwd.data.PuzzleEntity
import com.allaway.xwd.data.PuzzleRepository
import com.allaway.xwd.data.Settings
import com.allaway.xwd.data.XwdDatabase
import com.allaway.xwd.sources.Fetch
import com.allaway.xwd.sources.PuzzleDownloader
import com.allaway.xwd.sources.PuzzleDownloader.CatalogEntry
import com.allaway.xwd.sources.PuzzleSource
import com.allaway.xwd.sources.PuzzleSources
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PuzzleRepository(XwdDatabase.get(application).puzzleDao())
    private val downloader = PuzzleDownloader()

    val puzzles: StateFlow<List<PuzzleEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var downloading: Boolean by mutableStateOf(false)
        private set
    var message: String? by mutableStateOf(null)

    val sources: List<PuzzleSource> = PuzzleSources.all

    /** Sources with a browseable per-date archive. */
    val datedSources: List<PuzzleSource> = PuzzleSources.dated

    /** Source ids the user has toggled off; persisted across launches. */
    var disabledSources: Set<String> by mutableStateOf(Settings.getDisabledSources(application))
        private set

    /** Catalog entries discovered so far (not yet filtered against the library). */
    var remote: List<LibraryItem.Remote> by mutableStateOf(emptyList())
        private set
    var loadingMore: Boolean by mutableStateOf(false)
        private set
    var catalogExhausted: Boolean by mutableStateOf(false)
        private set

    /** Library ids currently being downloaded (per-card spinners). */
    var downloadingIds: Set<String> by mutableStateOf(emptySet())
        private set

    // Per-source catalog cursors.
    private val datedCursor = HashMap<String, LocalDate>()
    private val scrapedNextPage = HashMap<String, Int>()
    private val scrapedListed = HashMap<String, Int>()
    private val exhausted = HashSet<String>()

    init {
        loadMore()
    }

    /** The feed: saved puzzles plus not-yet-downloaded catalog entries, newest first. */
    fun feed(saved: List<PuzzleEntity>): List<LibraryItem> {
        val visibleSaved = saved.filter { it.sourceId == "photo" || it.sourceId !in disabledSources }
        val savedIds = saved.mapTo(HashSet()) { it.id }
        val visibleRemote = remote.filter { it.entry.sourceId !in disabledSources && it.id !in savedIds }
        return (visibleSaved.map { LibraryItem.Saved(it, parseDate(it.date)) } + visibleRemote)
            .sortedWith(
                compareByDescending<LibraryItem> { it.sortDate }
                    .thenBy { it is LibraryItem.Remote }
                    .thenBy { it.id },
            )
    }

    fun toggleSource(id: String) {
        disabledSources = if (id in disabledSources) disabledSources - id else disabledSources + id
        Settings.setDisabledSources(getApplication(), disabledSources)
        if (id !in disabledSources && id !in exhausted &&
            id !in datedCursor && id !in scrapedNextPage
        ) {
            loadMore() // first page for a source enabled after startup
        }
    }

    /** Pull the next page of every enabled source's catalog. Listing only, no .puz downloads. */
    fun loadMore() {
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            val discovered = ArrayList<LibraryItem.Remote>()
            val today = LocalDate.now()
            for (source in sources) {
                if (source.id in disabledSources || source.id in exhausted) continue
                when (val fetch = source.fetch) {
                    is Fetch.Dated -> {
                        val cursor = datedCursor[source.id] ?: today.plusDays(2)
                        val entries = downloader.listDated(source, cursor, DATED_PAGE_SIZE)
                        if (entries.isEmpty()) {
                            exhausted.add(source.id)
                        } else {
                            datedCursor[source.id] = entries.last().date!!.minusDays(1)
                            entries.mapTo(discovered) { LibraryItem.Remote(it, it.date!!) }
                        }
                    }
                    is Fetch.LatestFromPage -> {
                        val page = scrapedNextPage[source.id] ?: 1
                        try {
                            val entries = downloader.listScraped(source, page)
                            if (entries.isEmpty()) {
                                exhausted.add(source.id)
                            } else {
                                if (fetch.archivePageUrl == null) exhausted.add(source.id)
                                else scrapedNextPage[source.id] = page + 1
                                val offset = scrapedListed.getOrDefault(source.id, 0)
                                scrapedListed[source.id] = offset + entries.size
                                // No publish dates on scraped feeds; approximate
                                // recency from list position so cards interleave
                                // sensibly with dated sources.
                                entries.mapIndexedTo(discovered) { i, entry ->
                                    LibraryItem.Remote(entry, today.minusDays((offset + i) * 3L))
                                }
                            }
                        } catch (e: Exception) {
                            exhausted.add(source.id)
                            message = "${source.name}: ${e.message ?: "couldn't list puzzles"}"
                        }
                    }
                }
            }
            remote = (remote + discovered).distinctBy { it.id }
            catalogExhausted = sources.all { it.id in exhausted || it.id in disabledSources }
            loadingMore = false
        }
    }

    /** Download one catalog entry into the library. */
    fun download(entry: CatalogEntry) {
        val itemId = "${entry.sourceId}-${entry.uniqueKey}"
        if (itemId in downloadingIds) return
        val source = PuzzleSources.byId(entry.sourceId) ?: return
        downloadingIds = downloadingIds + itemId
        viewModelScope.launch {
            try {
                val entity = repo.downloadEntry(source, entry)
                if (entity == null) message = "${entry.title} is no longer available"
            } catch (e: Exception) {
                message = "${source.name}: ${e.message ?: "download failed"}"
            } finally {
                downloadingIds = downloadingIds - itemId
            }
        }
    }

    /** Fetch the newest available puzzle from every enabled source. */
    fun downloadLatestFromAll() {
        if (downloading) return
        downloading = true
        viewModelScope.launch {
            val added = ArrayList<String>()
            val errors = ArrayList<String>()
            for (source in sources) {
                if (source.id in disabledSources) continue
                try {
                    repo.downloadLatest(source)?.let { added.add("${source.name} ${it.date}") }
                } catch (e: Exception) {
                    errors.add("${source.name}: ${e.message ?: "download failed"}")
                }
            }
            message = when {
                errors.isNotEmpty() -> errors.joinToString("\n")
                added.isEmpty() -> "You're all caught up. No new puzzles."
                else -> "Added ${added.joinToString(", ")}"
            }
            downloading = false
        }
    }

    /** Fetch a specific date from one source (e.g. picked from the archive dialog). */
    fun downloadFor(source: PuzzleSource, epochMillis: Long) {
        if (downloading) return
        downloading = true
        viewModelScope.launch {
            val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            message = try {
                val entity = repo.downloadFor(source, date)
                if (entity != null) "Added ${source.name} $date"
                else "${source.name} has no puzzle for $date"
            } catch (e: Exception) {
                "${source.name}: ${e.message ?: "download failed"}"
            }
            downloading = false
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    companion object {
        private const val DATED_PAGE_SIZE = 10

        private fun parseDate(iso: String): LocalDate =
            try {
                LocalDate.parse(iso)
            } catch (_: Exception) {
                LocalDate.now()
            }
    }
}
