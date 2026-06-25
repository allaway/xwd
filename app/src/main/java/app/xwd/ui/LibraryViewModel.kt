package app.xwd.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xwd.data.CatalogEntity
import app.xwd.data.CatalogRepository
import app.xwd.data.PuzzleEntity
import app.xwd.data.PuzzleRepository
import app.xwd.data.Settings
import app.xwd.data.SourceRegistry
import app.xwd.data.XwdDatabase
import app.xwd.model.SizeClass
import app.xwd.sources.Fetch
import app.xwd.sources.PuzzleDownloader.CatalogEntry
import app.xwd.sources.PuzzleSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PuzzleRepository(XwdDatabase.get(application).puzzleDao())
    private val catalogRepo = CatalogRepository(XwdDatabase.get(application).catalogDao())

    val puzzles: StateFlow<List<PuzzleEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The persistent record of puzzles available for download. */
    val catalog: StateFlow<List<CatalogEntity>> = catalogRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var downloading: Boolean by mutableStateOf(false)
        private set
    var message: String? by mutableStateOf(null)

    /** True once the first Room emission has arrived; gates the empty-library message. */
    var isDataReady: Boolean by mutableStateOf(false)
        private set

    /** Built-in sources plus the user's custom feeds; reloaded on resume. */
    var sources: List<PuzzleSource> by mutableStateOf(SourceRegistry.resolved(application))
        private set

    /** Sources with a browseable per-date archive (offered in the date dialog). */
    val datedSources: List<PuzzleSource>
        get() = sources.filter { it.fetch is Fetch.Dated }

    /** Source ids the user has toggled off; persisted across launches. */
    var disabledSources: Set<String> by mutableStateOf(Settings.getDisabledSources(application))
        private set

    /** Sources shown in the library's filter row: everything not turned off. */
    val enabledSources: List<PuzzleSource>
        get() = sources.filter { it.id !in disabledSources }

    /** The current view filters: status, source, and size. */
    var filters: LibraryFilters by mutableStateOf(LibraryFilters())
        private set

    var loadingMore: Boolean by mutableStateOf(false)
        private set
    var catalogExhausted: Boolean by mutableStateOf(false)
        private set

    /** Library ids currently being downloaded (per-card spinners). */
    var downloadingIds: Set<String> by mutableStateOf(emptySet())
        private set

    // Next archive page per scraped source; persisted across launches.
    // Page 1 is always covered by refreshNewest, so paging starts at 2.
    private val pageCursors =
        Settings.getCatalogPageCursors(application).toMutableMap()
    private val exhausted = HashSet<String>()
    private var refreshing = false

    init {
        viewModelScope.launch {
            // Wait for the first DB emission so we don't show the empty-library
            // message during the brief moment before Room delivers its snapshot.
            repo.observeAll().first()
            isDataReady = true
        }
        refreshNewest()
    }

    /**
     * Re-read feed configuration (custom feeds added or sources toggled in
     * Settings) and list anything newly enabled. Called when the library
     * regains focus.
     */
    fun reloadConfig() {
        val app = getApplication<Application>()
        sources = SourceRegistry.resolved(app)
        disabledSources = Settings.getDisabledSources(app)
        // A source narrowed-to but now disabled shouldn't keep the view empty.
        if (filters.sourceId != null && filters.sourceId in disabledSources) {
            filters = filters.copy(sourceId = null)
        }
        refreshNewest()
    }

    private val crypticSourceIds: Set<String>
        get() = sources.filter { it.isCryptic }.map { it.id }.toSet()

    /** The feed: the catalog joined against saved puzzles, in stable order. */
    fun feed(saved: List<PuzzleEntity>, catalog: List<CatalogEntity>): List<LibraryItem> =
        LibraryFeed.build(saved, catalog, disabledSources, crypticSourceIds, filters)

    fun setDownloadedOnly(value: Boolean) {
        filters = filters.copy(downloadedOnly = value)
    }

    fun setSourceFilter(id: String?) {
        filters = filters.copy(sourceId = id)
    }

    fun setSizeFilter(size: SizeClass?) {
        filters = filters.copy(size = size)
    }

    fun setPuzzleTypeFilter(type: PuzzleType?) {
        filters = filters.copy(puzzleType = type)
    }

    fun setSortOrder(order: SortOrder) {
        filters = filters.copy(sortOrder = order)
    }

    fun clearFilters() {
        filters = LibraryFilters()
    }

    /** Display name of the filtered source, for the filter summary line. */
    fun sourceNameOf(id: String): String = sources.firstOrNull { it.id == id }?.name ?: id

    /**
     * Check every enabled source for newly published puzzles and record them
     * in the catalog. Silent: failures here (flaky network at launch) don't
     * surface; the background worker retries twice a day anyway.
     */
    private fun refreshNewest() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            for (source in sources) {
                if (source.id in disabledSources) continue
                try {
                    catalogRepo.refreshNewest(source)
                } catch (_: Exception) {
                    // ignored: catalog keeps whatever was already recorded
                }
            }
            refreshing = false
        }
    }

    /** Extend the catalog one page deeper into every enabled source's archive. */
    fun loadMore() {
        // Nothing to fetch when the view is limited to on-device puzzles
        // (downloaded-only, or a specific size, which only saved puzzles have).
        if (loadingMore || filters.downloadedOnly || filters.size != null) return
        loadingMore = true
        viewModelScope.launch {
            for (source in sources) {
                if (source.id in disabledSources || source.id in exhausted) continue
                try {
                    val listed = when (val fetch = source.fetch) {
                        is Fetch.Dated -> catalogRepo.loadOlderDated(source)
                        is Fetch.LatestFromPage -> {
                            if (fetch.archivePageUrl == null) {
                                0 // front page only; refreshNewest covers it
                            } else {
                                val page = pageCursors.getOrDefault(source.id, 2)
                                val n = catalogRepo.loadOlderScraped(source, page)
                                if (n > 0) {
                                    pageCursors[source.id] = page + 1
                                    Settings.setCatalogPageCursors(getApplication(), pageCursors)
                                }
                                n
                            }
                        }
                    }
                    if (listed == 0) exhausted.add(source.id)
                } catch (e: Exception) {
                    exhausted.add(source.id)
                    message = "${source.name}: ${e.message ?: "couldn't list puzzles"}"
                }
            }
            catalogExhausted = sources.all { it.id in exhausted || it.id in disabledSources }
            loadingMore = false
        }
    }

    /** Download one catalog entry into the library. */
    fun download(entry: CatalogEntry) {
        val itemId = "${entry.sourceId}-${entry.uniqueKey}"
        if (itemId in downloadingIds) return
        val source = sources.firstOrNull { it.id == entry.sourceId } ?: return
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
}
