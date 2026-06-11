package com.allaway.xwd.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.allaway.xwd.sources.PuzzleSources
import java.util.concurrent.TimeUnit

/**
 * Silently refreshes the catalog of downloadable puzzles twice a day, so
 * new dailies appear in the library feed without a manual refresh. Only
 * the listings are fetched; puzzle files still download on demand.
 */
class CatalogRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = CatalogRepository(XwdDatabase.get(applicationContext).catalogDao())
        val disabled = Settings.getDisabledSources(applicationContext)
        var failed = 0
        var tried = 0
        for (source in PuzzleSources.all) {
            if (source.id in disabled) continue
            tried++
            try {
                repo.refreshNewest(source)
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
