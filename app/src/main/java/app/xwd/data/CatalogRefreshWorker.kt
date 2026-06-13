package app.xwd.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Silently refreshes the catalog of downloadable puzzles twice a day, so
 * new dailies appear in the library feed without a manual refresh. Only the
 * listings are fetched; puzzle files download on demand — unless the user
 * turned on prospective auto-download, in which case newly listed puzzles
 * from enabled feeds are fetched too.
 */
class CatalogRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = XwdDatabase.get(applicationContext)
        val catalogRepo = CatalogRepository(db.catalogDao())
        val puzzleRepo = PuzzleRepository(db.puzzleDao())
        val disabled = Settings.getDisabledSources(applicationContext)
        val autoDownload = Settings.getAutoDownloadProspective(applicationContext)
        var failed = 0
        var tried = 0
        for (source in SourceRegistry.resolved(applicationContext)) {
            if (source.id in disabled) continue
            tried++
            try {
                val fresh = catalogRepo.refreshNewest(source)
                if (autoDownload) {
                    for (entry in fresh) {
                        try {
                            puzzleRepo.downloadEntry(source, entry)
                        } catch (_: Exception) {
                            // a single bad file shouldn't abort the batch
                        }
                    }
                }
            } catch (_: Exception) {
                failed++ // one broken feed must not stop the rest
            }
        }
        // Everything failing usually means no usable network; try again later.
        return if (tried > 0 && failed == tried) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "catalog-refresh"

        /** Idempotent; safe to call on every app launch. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
