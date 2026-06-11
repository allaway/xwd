package app.xwd.sources

import app.xwd.model.Puzzle
import app.xwd.puz.PuzzleFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    /**
     * A puzzle known to exist in a source's feed, listed without downloading
     * the .puz file itself. [date] is known only for dated feeds.
     */
    data class CatalogEntry(
        val sourceId: String,
        val uniqueKey: String,
        val title: String,
        val date: LocalDate?,
        val url: String,
    )

    /**
     * List a page of a dated source's archive without any network traffic:
     * publication dates are derived from the source's schedule.
     */
    fun listDated(source: PuzzleSource, newestInclusive: LocalDate, count: Int): List<CatalogEntry> {
        val fetch = source.fetch as? Fetch.Dated
            ?: throw IllegalArgumentException("${source.name} has no per-date archive")
        return fetch.archiveDates(newestInclusive, count).map { date ->
            CatalogEntry(
                sourceId = source.id,
                uniqueKey = date.toString(),
                title = "${source.name} ${date.format(DISPLAY_DATE)}",
                date = date,
                url = fetch.urlFor(date),
            )
        }
    }

    /**
     * List the puzzles linked from a scraped source's index page. Page 1 is
     * the front page; older pages need [Fetch.LatestFromPage.archivePageUrl].
     * Only the HTML index is fetched, never the .puz files.
     */
    suspend fun listScraped(source: PuzzleSource, page: Int): List<CatalogEntry> {
        val fetch = source.fetch as? Fetch.LatestFromPage
            ?: throw IllegalArgumentException("${source.name} is not a scraped source")
        val pageUrl = when {
            page <= 1 -> fetch.pageUrl
            else -> fetch.archivePageUrl?.invoke(page) ?: return emptyList()
        }
        val html = getBytes(pageUrl, source.name)?.decodeToString() ?: return emptyList()
        return extractAllPuzUrls(html, fetch).map { url ->
            val key = puzUrlKey(url)
            CatalogEntry(
                sourceId = source.id,
                uniqueKey = key,
                title = humanizeSlug(key),
                date = null,
                url = url,
            )
        }
    }

    /** Download a puzzle previously listed in the catalog. Null if it has vanished. */
    suspend fun fetchEntry(source: PuzzleSource, entry: CatalogEntry): Downloaded? {
        val referer = (source.fetch as? Fetch.LatestFromPage)?.pageUrl
        val bytes = getBytes(entry.url, source.name, referer = referer) ?: return null
        return Downloaded(PuzzleFormats.parse(bytes), entry.date ?: LocalDate.now(), entry.uniqueKey)
    }

    /** Fetch the puzzle of a dated source for a specific day, or null if absent. */
    suspend fun fetch(source: PuzzleSource, date: LocalDate): Downloaded? {
        val fetch = source.fetch as? Fetch.Dated
            ?: throw IllegalArgumentException("${source.name} has no per-date archive")
        val bytes = getBytes(fetch.urlFor(date), source.name) ?: return null
        return Downloaded(PuzzleFormats.parse(bytes), date, date.toString())
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
        return Downloaded(PuzzleFormats.parse(bytes), LocalDate.now(), key)
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
        private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0 Mobile Safari/537.36"

        /** First match on the page is the newest post's puzzle. */
        fun extractLatestPuzUrl(html: String, fetch: Fetch.LatestFromPage): String? {
            val capture = fetch.linkPattern.find(html)?.groupValues?.get(1) ?: return null
            return directDownloadUrl(fetch.resolveUrl(unescapeHtml(capture)))
        }

        /** Every puzzle linked on the page, newest first, deduplicated by key. */
        fun extractAllPuzUrls(html: String, fetch: Fetch.LatestFromPage): List<String> =
            fetch.linkPattern.findAll(html)
                .map { directDownloadUrl(fetch.resolveUrl(unescapeHtml(it.groupValues[1]))) }
                .distinctBy { puzUrlKey(it) }
                .toList()

        /**
         * Dropbox share links serve an HTML preview page unless dl=1; some
         * blogs (e.g. JKL Crosswords) link the dl=0 form. Rewrite to the
         * raw-file form so the download is the puzzle, not the page.
         */
        fun directDownloadUrl(url: String): String = when {
            !url.contains("dropbox.com") -> url
            url.contains("dl=0") -> url.replace("dl=0", "dl=1")
            url.contains("dl=1") -> url
            url.contains('?') -> "$url&dl=1"
            else -> "$url?dl=1"
        }

        /** "Puzzle1201Freestyle1122" -> "Puzzle 1201 Freestyle 1122". */
        fun humanizeSlug(slug: String): String =
            slug.replace(Regex("[-_]+"), " ")
                .replace(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])"), " ")
                .trim()

        private fun unescapeHtml(s: String): String =
            s.replace("&amp;", "&").replace("&#038;", "&").replace("&#38;", "&")

        /**
         * Stable identity for a scraped puzzle: its file name without
         * extension, percent-decoded so keys (and the titles derived from
         * them) read "Letter for Letter", not "Letter%20for%20Letter".
         */
        fun puzUrlKey(url: String): String {
            val raw = url.substringBefore('?').substringAfterLast('/')
                .removeSuffix(".zip").removeSuffix(".ipuz").removeSuffix(".puz")
            return try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: Exception) {
                raw
            }
        }
    }
}
