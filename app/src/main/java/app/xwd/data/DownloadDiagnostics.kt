package app.xwd.data

import app.xwd.puz.PuzFormatException
import app.xwd.sources.HttpStatusException
import java.io.IOException
import java.io.InterruptedIOException

/** Why a bulk download attempt didn't produce a puzzle, for the run summary. */
enum class FailureReason(val label: String) {
    NOT_FOUND("not found (404)"),
    TIMED_OUT("timed out"),
    RATE_LIMITED("rate-limited"),
    SERVER_ERROR("server error"),
    UNREADABLE("unreadable file"),
    NETWORK("network error"),
    OTHER("other error"),
}

object DownloadDiagnostics {

    /**
     * Bucket a download failure so the summary can show *why* puzzles failed
     * (rate-limited vs timed out vs genuinely gone) instead of one opaque
     * count. A null result (the feed no longer offers the file) is [NOT_FOUND].
     */
    fun classify(t: Throwable): FailureReason = when {
        t is PuzFormatException -> FailureReason.UNREADABLE
        t is HttpStatusException -> when (t.code) {
            // 503/429/408/425 mean "slow down / try later"; 403 is usually a
            // WAF throttle for this kind of bulk access.
            403, 408, 425, 429, 503 -> FailureReason.RATE_LIMITED
            in 500..599 -> FailureReason.SERVER_ERROR
            else -> FailureReason.OTHER
        }
        // SocketTimeoutException is an InterruptedIOException.
        t is InterruptedIOException -> FailureReason.TIMED_OUT
        t is IOException -> FailureReason.NETWORK
        else -> FailureReason.OTHER
    }
}
