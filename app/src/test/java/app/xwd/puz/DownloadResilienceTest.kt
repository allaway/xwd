package app.xwd.puz

import app.xwd.data.DownloadDiagnostics
import app.xwd.data.FailureReason
import app.xwd.sources.HttpRetry
import app.xwd.sources.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class DownloadResilienceTest {

    @Test
    fun retriesTransientCodesButNotPermanentOnes() {
        assertTrue(HttpRetry.shouldRetry(429, attempt = 0))
        assertTrue(HttpRetry.shouldRetry(503, attempt = 0))
        assertTrue(HttpRetry.shouldRetry(500, attempt = 2))
        // Last allowed attempt: no more retries.
        assertFalse(HttpRetry.shouldRetry(429, attempt = HttpRetry.MAX_ATTEMPTS - 1))
        // Permanent statuses are never retried.
        assertFalse(HttpRetry.shouldRetry(404, attempt = 0))
        assertFalse(HttpRetry.shouldRetry(403, attempt = 0))
        assertFalse(HttpRetry.shouldRetry(200, attempt = 0))
        // Timeouts retry fewer times than rate-limits (each one is expensive).
        assertTrue(HttpRetry.MAX_NETWORK_ATTEMPTS < HttpRetry.MAX_ATTEMPTS)
    }

    @Test
    fun backoffIsExponentialAndHonorsRetryAfter() {
        assertEquals(500L, HttpRetry.retryDelayMillis(0))
        assertEquals(1000L, HttpRetry.retryDelayMillis(1))
        assertEquals(2000L, HttpRetry.retryDelayMillis(2))
        // A server Retry-After (seconds) wins over backoff, capped at 30s.
        assertEquals(5000L, HttpRetry.retryDelayMillis(0, retryAfterSeconds = 5))
        assertEquals(30_000L, HttpRetry.retryDelayMillis(0, retryAfterSeconds = 120))
    }

    @Test
    fun parsesRetryAfterSecondsHeader() {
        assertEquals(7L, HttpRetry.parseRetryAfter("7"))
        assertEquals(7L, HttpRetry.parseRetryAfter("  7 "))
        assertEquals(null, HttpRetry.parseRetryAfter(null))
        // HTTP-date form isn't supported; we just fall back to backoff.
        assertEquals(null, HttpRetry.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun classifiesFailuresByCause() {
        assertEquals(FailureReason.UNREADABLE, DownloadDiagnostics.classify(PuzFormatException("bad")))
        assertEquals(FailureReason.RATE_LIMITED, DownloadDiagnostics.classify(HttpStatusException(429, "x")))
        assertEquals(FailureReason.RATE_LIMITED, DownloadDiagnostics.classify(HttpStatusException(503, "x")))
        assertEquals(FailureReason.RATE_LIMITED, DownloadDiagnostics.classify(HttpStatusException(403, "x")))
        assertEquals(FailureReason.SERVER_ERROR, DownloadDiagnostics.classify(HttpStatusException(500, "x")))
        assertEquals(FailureReason.OTHER, DownloadDiagnostics.classify(HttpStatusException(418, "x")))
        assertEquals(FailureReason.TIMED_OUT, DownloadDiagnostics.classify(SocketTimeoutException("slow")))
        assertEquals(FailureReason.TIMED_OUT, DownloadDiagnostics.classify(InterruptedIOException("slow")))
        assertEquals(FailureReason.NETWORK, DownloadDiagnostics.classify(IOException("reset")))
        assertEquals(FailureReason.OTHER, DownloadDiagnostics.classify(RuntimeException("?")))
    }
}
