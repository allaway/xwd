package app.xwd.data

import android.content.Context

/** Tiny settings store: the user's Claude API key and hidden puzzle sources. */
object Settings {
    private const val PREFS = "xwd_settings"
    private const val KEY_API_KEY = "anthropic_api_key"
    private const val KEY_DISABLED_SOURCES = "disabled_sources"

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, value.trim())
            .apply()
    }

    private const val KEY_CATALOG_PAGES = "catalog_page_cursors"

    /**
     * Next archive page to list per scraped source, persisted so browsing
     * deeper into an archive resumes where it left off across launches.
     */
    fun getCatalogPageCursors(context: Context): Map<String, Int> =
        (
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CATALOG_PAGES, "") ?: ""
            )
            .split(',')
            .mapNotNull { token ->
                val (id, page) = token.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                page.toIntOrNull()?.let { id to it }
            }
            .toMap()

    fun setCatalogPageCursors(context: Context, cursors: Map<String, Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATALOG_PAGES, cursors.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    /** Source ids the user has toggled off in the library. Empty = all sources on. */
    fun getDisabledSources(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DISABLED_SOURCES, emptySet()) ?: emptySet()

    fun setDisabledSources(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DISABLED_SOURCES, ids)
            .apply()
    }
}
