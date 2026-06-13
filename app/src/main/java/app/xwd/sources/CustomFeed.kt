package app.xwd.sources

import kotlinx.serialization.Serializable

/**
 * A puzzle feed the user added themselves: a web page (typically a
 * constructor's blog or downloads page) that links to .puz / .ipuz files.
 * Scraped exactly like a built-in [Fetch.LatestFromPage] source.
 */
@Serializable
data class CustomFeed(
    val id: String,
    val name: String,
    /** URL of a page that links to .puz or .ipuz files. */
    val pageUrl: String,
)
