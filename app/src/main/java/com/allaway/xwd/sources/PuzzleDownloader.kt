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

    /**
     * @param uniqueKey stable identity of this puzzle within its source
     *   (the ISO date for dated feeds, the file slug for scraped feeds),
     *   used to avoid re-downloading puzzles already in the library.
     */
    data class Downloaded(val puzzle: Puzzle, val date: LocalDate, val uniqueKey: String)

    /** Fetch the puzzle of a dated source for a specific day, or null if absent. */
    suspend fun fetch(source: PuzzleSource, date: LocalDate): Downloaded? {
        val fetch = source.fetch as? Fetch.Dated
            ?: throw IllegalArgumentException("${source.name} has no per-date archive")
        val bytes = getBytes(fetch.urlFor(date), source.name) ?: return null
        return Downloaded(PuzParser.parse(bytes), date, date.toString())
    }

    /**
     * Fetch the most recent available puzzle, skipping puzzles whose unique
     * keys appear in [alreadyHave]. Returns null when the library is current.
     */
    suspend fun fetchLatest(source: PuzzleSource, alreadyHave: Set<String>): Downloaded? =
        when (val fetch = source.fetch) {
            is Fetch.Dated -> {
                var result: Downloaded? = null
                for (date in fetch.candidateDates()) {
                    if (date.toString() in alreadyHave) continue
                    result = fetch(source, date)
                    if (result != null) break
                }
                result
            }
            is Fetch.LatestFromPage -> fetchLatestFromPage(source, fetch, alreadyHave)
        }

    private suspend fun fetchLatestFromPage(
        source: PuzzleSource,
        fetch: Fetch.LatestFromPage,
        alreadyHave: Set<String>,
    ): Downloaded? {
        val html = getBytes(fetch.pageUrl, source.name)?.decodeToString()
            ?: throw IOException("${source.name} page unavailable")
        val url = extractLatestPuzUrl(html, fetch) ?: return null
        val key = puzUrlKey(url)
        if (key in alreadyHave) return null
        val bytes = getBytes(url, source.name, referer = fetch.pageUrl) ?: return null
        return Downloaded(PuzParser.parse(bytes), LocalDate.now(), key)
    }

    private suspend fun getBytes(url: String, sourceName: String, referer: String? = null): ByteArray? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                // Some hosts (e.g. WordPress security plugins) reject non-browser
                // user agents with 406/404, so present a browser-style identity.
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "*/*")
                .apply { referer?.let { header("Referer", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> null
                    !response.isSuccessful -> throw IOException("HTTP ${response.code} from $sourceName")
                    else -> response.body?.bytes() ?: throw IOException("Empty response")
                }
            }
        }

    companion object {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0 Mobile Safari/537.36"

        /** First .puz link on the page is the newest post's puzzle. */
        fun extractLatestPuzUrl(html: String, fetch: Fetch.LatestFromPage): String? {
            val href = fetch.linkPattern.find(html)?.groupValues?.get(1) ?: return null
            return if (href.startsWith("http")) href else fetch.baseUrl + href
        }

        /** Stable identity for a scraped puzzle: its file name without extension. */
        fun puzUrlKey(url: String): String =
            url.substringAfterLast('/').removeSuffix(".puz")
    }
}
