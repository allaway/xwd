package com.allaway.xwd.sources

import com.allaway.xwd.model.Puzzle
import com.allaway.xwd.puz.PuzParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class PuzzleDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    data class Downloaded(val puzzle: Puzzle, val date: LocalDate)

    /** Fetch the puzzle for a specific date, or null if the feed has none. */
    suspend fun fetch(source: PuzzleSource, date: LocalDate): Downloaded? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(source.urlFor(date))
                .header("User-Agent", "xwd-android/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> null
                    !response.isSuccessful -> throw IOException("HTTP ${response.code} from ${source.name}")
                    else -> {
                        val body = response.body?.bytes() ?: throw IOException("Empty response")
                        Downloaded(PuzParser.parse(body), date)
                    }
                }
            }
        }

    /**
     * Fetch the most recent available puzzle, skipping dates already in
     * [alreadyHave] (ISO local-date strings).
     */
    suspend fun fetchLatest(source: PuzzleSource, alreadyHave: Set<String>): Downloaded? {
        for (date in source.candidateDates()) {
            if (date.toString() in alreadyHave) continue
            val result = fetch(source, date)
            if (result != null) return result
        }
        return null
    }
}
