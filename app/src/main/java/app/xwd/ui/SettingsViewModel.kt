package app.xwd.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xwd.data.Backup
import app.xwd.data.BackupRepository
import app.xwd.data.BulkDownload
import app.xwd.data.CatalogRepository
import app.xwd.data.CatalogRepository.Companion.toCatalogEntry
import app.xwd.data.DownloadDiagnostics
import app.xwd.data.ExportResult
import app.xwd.data.FailureReason
import app.xwd.data.PuzzleRepository
import app.xwd.data.RestoreResult
import app.xwd.data.Settings
import app.xwd.data.SourceRegistry
import app.xwd.data.XwdDatabase
import app.xwd.sources.CustomFeed
import app.xwd.sources.PuzzleSource
import app.xwd.sources.PuzzleSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Progress of the "download history" / bulk-download flow. */
sealed interface BulkState {
    data object Idle : BulkState

    /** Listing archives. [done]/[total] sources scanned, [found] puzzles listed so far. */
    data class Scanning(val done: Int, val total: Int, val found: Int) : BulkState

    /** Scan finished: [pending] puzzles are available to download. */
    data class Ready(val pending: Int) : BulkState

    data class Downloading(val done: Int, val total: Int, val failed: Int) : BulkState

    /**
     * Run complete. [failures] breaks the failures down by reason so the user
     * (and we) can see whether they were rate-limits, timeouts, or genuinely
     * missing files. Transient ones clear on a retry.
     */
    data class Finished(
        val downloaded: Int,
        val failures: Map<FailureReason, Int>,
    ) : BulkState {
        val failed: Int get() = failures.values.sum()
        val retryable: Int get() = failures.entries
            .filter { it.key != FailureReason.NOT_FOUND }
            .sumOf { it.value }
    }
}

/** Progress of moving the library to or from a backup file. */
sealed interface TransferState {
    data object Idle : TransferState

    data class Exporting(val done: Int, val total: Int) : TransferState

    data class Restoring(val done: Int, val total: Int) : TransferState

    data class Exported(val result: ExportResult) : TransferState

    data class Restored(val result: RestoreResult) : TransferState

    data class Failed(val message: String) : TransferState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = XwdDatabase.get(application)
    private val catalogRepo = CatalogRepository(db.catalogDao())
    private val puzzleRepo = PuzzleRepository(db.puzzleDao())
    private val backupRepo = BackupRepository(application, db)

    var sources: List<PuzzleSource> by mutableStateOf(SourceRegistry.resolved(application))
        private set
    var disabledSources: Set<String> by mutableStateOf(Settings.getDisabledSources(application))
        private set
    var customFeeds: List<CustomFeed> by mutableStateOf(Settings.getCustomFeeds(application))
        private set

    var autocheckDefault: Boolean by mutableStateOf(Settings.getAutocheckDefault(application))
        private set
    var autoDownloadProspective: Boolean by mutableStateOf(
        Settings.getAutoDownloadProspective(application),
    )
        private set

    var bulk: BulkState by mutableStateOf(BulkState.Idle)
        private set
    var message: String? by mutableStateOf(null)

    var transfer: TransferState by mutableStateOf(TransferState.Idle)
        private set

    /** Whether an exported backup should carry the Claude API key too. */
    var includeApiKeyInBackup: Boolean by mutableStateOf(false)
        private set

    /** Only offer the API key toggle when there is a key to carry. */
    var hasApiKey: Boolean by mutableStateOf(Settings.getApiKey(application).isNotBlank())
        private set

    /**
     * Skin name a restore brought in, for the UI to apply live; the screen
     * clears it with [consumeRestoredSkin] once it has.
     */
    var restoredSkinName: String? by mutableStateOf(null)
        private set

    /** Suggested file name for the export document picker. */
    val backupFileName: String get() = Backup.fileName(LocalDate.now().toString())

    private var bulkJob: Job? = null
    private var transferJob: Job? = null

    fun updateAutocheckDefault(value: Boolean) {
        autocheckDefault = value
        Settings.setAutocheckDefault(getApplication(), value)
    }

    fun updateAutoDownloadProspective(value: Boolean) {
        autoDownloadProspective = value
        Settings.setAutoDownloadProspective(getApplication(), value)
    }

    fun isEnabled(id: String): Boolean = id !in disabledSources

    fun setSourceEnabled(id: String, enabled: Boolean) {
        disabledSources = if (enabled) disabledSources - id else disabledSources + id
        Settings.setDisabledSources(getApplication(), disabledSources)
    }

    /** Add a custom feed from a page URL; returns an error message, or null on success. */
    fun addCustomFeed(name: String, url: String): String? {
        val cleanName = name.trim()
        var cleanUrl = url.trim()
        if (cleanName.isEmpty()) return "Give the feed a name."
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            if (cleanUrl.isBlank()) return "Enter the feed's web address."
            cleanUrl = "https://$cleanUrl"
        }
        val feed = CustomFeed(
            id = PuzzleSources.CUSTOM_PREFIX + System.currentTimeMillis().toString(36),
            name = cleanName,
            pageUrl = cleanUrl,
        )
        val updated = customFeeds + feed
        persistCustomFeeds(updated)
        // List its newest puzzles right away so it isn't empty.
        viewModelScope.launch {
            try {
                catalogRepo.refreshNewest(PuzzleSources.fromCustom(feed))
            } catch (e: Exception) {
                message = "${feed.name}: ${e.message ?: "couldn't list puzzles"}"
            }
        }
        return null
    }

    fun removeCustomFeed(id: String) {
        persistCustomFeeds(customFeeds.filterNot { it.id == id })
        if (id in disabledSources) setSourceEnabled(id, enabled = true)
    }

    private fun persistCustomFeeds(feeds: List<CustomFeed>) {
        customFeeds = feeds
        Settings.setCustomFeeds(getApplication(), feeds)
        sources = SourceRegistry.resolved(getApplication())
    }

    /**
     * Walk every enabled source's archive, recording all available puzzles
     * in the catalog, then report how many are not yet downloaded.
     */
    fun scanArchives() {
        if (bulk is BulkState.Scanning || bulk is BulkState.Downloading) return
        val enabled = sources.filter { it.id !in disabledSources }
        bulk = BulkState.Scanning(done = 0, total = enabled.size, found = 0)
        bulkJob = viewModelScope.launch {
            var found = 0
            enabled.forEachIndexed { i, source ->
                try {
                    catalogRepo.listEntireArchive(source) { added ->
                        bulk = BulkState.Scanning(done = i, total = enabled.size, found = found + added)
                    }
                    found = (bulk as? BulkState.Scanning)?.found ?: found
                } catch (_: Exception) {
                    // skip a feed that won't list; the rest still scan
                }
                bulk = BulkState.Scanning(done = i + 1, total = enabled.size, found = found)
            }
            val pending = currentPending()
            bulk = BulkState.Ready(pending.size)
        }
    }

    /**
     * Download every not-yet-downloaded puzzle from enabled sources. Files
     * already on the device are skipped (they're excluded from [currentPending]
     * and re-checked in the repository), so this is safe to run repeatedly —
     * a second run only retries what's still missing.
     */
    fun downloadPending() {
        if (bulk is BulkState.Scanning || bulk is BulkState.Downloading) return
        bulkJob = viewModelScope.launch {
            val pending = currentPending()
            if (pending.isEmpty()) {
                bulk = BulkState.Finished(downloaded = 0, failures = emptyMap())
                return@launch
            }
            val byId = sources.associateBy { it.id }
            var done = 0
            val failures = linkedMapOf<FailureReason, Int>()
            fun note(reason: FailureReason) { failures[reason] = (failures[reason] ?: 0) + 1 }

            bulk = BulkState.Downloading(done = 0, total = pending.size, failed = 0)
            for (row in pending) {
                val source = byId[row.sourceId]
                if (source == null) {
                    note(FailureReason.OTHER)
                } else {
                    try {
                        if (puzzleRepo.downloadEntry(source, row.toCatalogEntry()) == null) {
                            note(FailureReason.NOT_FOUND)
                        }
                    } catch (e: Exception) {
                        note(DownloadDiagnostics.classify(e))
                    }
                }
                done++
                // A short pause keeps us from hammering any one host into
                // rate-limiting the whole batch.
                delay(THROTTLE_MS)
                bulk = BulkState.Downloading(done, total = pending.size, failed = failures.values.sum())
            }
            bulk = BulkState.Finished(downloaded = done - failures.values.sum(), failures = failures)
        }
    }

    fun cancelBulk() {
        bulkJob?.cancel()
        bulkJob = null
        bulk = BulkState.Idle
    }

    fun dismissBulk() {
        bulk = BulkState.Idle
    }

    fun updateIncludeApiKeyInBackup(value: Boolean) {
        includeApiKeyInBackup = value
    }

    /** Write the whole library, progress, and settings to the picked file. */
    fun exportTo(uri: Uri) {
        if (transfer is TransferState.Exporting || transfer is TransferState.Restoring) return
        val context = getApplication<Application>()
        transfer = TransferState.Exporting(done = 0, total = 0)
        transferJob = viewModelScope.launch {
            try {
                val result = context.contentResolver.openOutputStream(uri, "wt").use { out ->
                    requireNotNull(out) { "couldn't open that file for writing" }
                    backupRepo.export(
                        out = out,
                        includeApiKey = includeApiKeyInBackup,
                        appVersionName = versionName(),
                    ) { done, total -> transfer = TransferState.Exporting(done, total) }
                }
                transfer = TransferState.Exported(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transfer = TransferState.Failed(e.message ?: "The backup couldn't be written.")
            }
        }
    }

    /** Merge a backup file into this device. */
    fun restoreFrom(uri: Uri) {
        if (transfer is TransferState.Exporting || transfer is TransferState.Restoring) return
        val context = getApplication<Application>()
        transfer = TransferState.Restoring(done = 0, total = 0)
        transferJob = viewModelScope.launch {
            try {
                val result = context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "couldn't open that file" }
                    backupRepo.restore(input) { done, total ->
                        transfer = TransferState.Restoring(done, total)
                    }
                }
                reloadFromSettings()
                transfer = TransferState.Restored(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transfer = TransferState.Failed(e.message ?: "That backup couldn't be read.")
            }
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        transfer = TransferState.Idle
    }

    fun dismissTransfer() {
        transfer = TransferState.Idle
    }

    fun consumeRestoredSkin() {
        restoredSkinName = null
    }

    /** Pull the restored preferences back into the screen's state. */
    private fun reloadFromSettings() {
        val context = getApplication<Application>()
        customFeeds = Settings.getCustomFeeds(context)
        disabledSources = Settings.getDisabledSources(context)
        autocheckDefault = Settings.getAutocheckDefault(context)
        autoDownloadProspective = Settings.getAutoDownloadProspective(context)
        hasApiKey = Settings.getApiKey(context).isNotBlank()
        sources = SourceRegistry.resolved(context)
        restoredSkinName = Settings.getSkinName(context).takeIf { it.isNotBlank() }
    }

    private fun versionName(): String = try {
        val context = getApplication<Application>()
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    private suspend fun currentPending() = BulkDownload.pending(
        catalog = db.catalogDao().all(),
        savedIds = db.puzzleDao().allIds().toSet(),
        disabledSources = disabledSources,
    )

    private companion object {
        /**
         * Small pause between bulk downloads. Kept short: the real protection
         * against rate-limits is the backoff/retry in the downloader, so this
         * is just a light floor, not the throttle that made runs crawl.
         */
        const val THROTTLE_MS = 40L
    }
}
