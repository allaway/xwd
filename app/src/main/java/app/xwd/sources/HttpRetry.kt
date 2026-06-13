package app.xwd.sources

import java.io.IOException

/** A non-2xx, non-404 HTTP response, carrying the status code for diagnostics. */
class HttpStatusException(val code: Int, val source: String) :
    IOException("HTTP $code from $source")

/**
 * Retry policy for puzzle downloads. Hosts that serve free puzzles (Crosshare's
 * API, Dropbox, WordPress blogs behind Cloudflare) rate-limit aggressively when
 * many files are pulled in quick succession — exactly what the "download full
 * history" tool does. Transient responses get a bounded, backed-off retry
 * instead of being counted as permanent failures.
 */
object HttpRetry {

    /** Codes worth retrying: rate-limit and transient server errors. Never 404. */
    val TRANSIENT_CODES = setOf(408, 425, 429, 500, 502, 503, 504)

    /** Total attempts (initial try plus retries) for a rate-limited/5xx response. */
    const val MAX_ATTEMPTS = 4

    /**
     * Total attempts for a network error (incl. timeouts). Lower than
     * [MAX_ATTEMPTS] because each timed-out attempt can burn the full read
     * timeout, so retrying many times would crawl a large bulk download.
     */
    const val MAX_NETWORK_ATTEMPTS = 2

    private const val BASE_DELAY_MS = 500L
    private const val MAX_DELAY_MS = 30_000L

    fun shouldRetry(code: Int, attempt: Int): Boolean =
        code in TRANSIENT_CODES && attempt + 1 < MAX_ATTEMPTS

    /**
     * How long to wait before retry [attempt] (0-based). Honors a server
     * `Retry-After` (in seconds) when given, otherwise exponential backoff:
     * 0.5s, 1s, 2s, ... capped at [MAX_DELAY_MS].
     */
    fun retryDelayMillis(attempt: Int, retryAfterSeconds: Long? = null): Long {
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            return (retryAfterSeconds * 1000).coerceIn(0, MAX_DELAY_MS)
        }
        val shifted = BASE_DELAY_MS shl attempt.coerceIn(0, 16)
        return shifted.coerceIn(BASE_DELAY_MS, MAX_DELAY_MS)
    }

    /** Parse a `Retry-After` header that uses the delta-seconds form. */
    fun parseRetryAfter(header: String?): Long? = header?.trim()?.toLongOrNull()
}
