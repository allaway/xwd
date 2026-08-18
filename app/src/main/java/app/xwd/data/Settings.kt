package app.xwd.data

import android.content.Context
import app.xwd.sources.CustomFeed
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Tiny settings store: API key, skin, feed config, and solving defaults. */
object Settings {
    private const val PREFS = "xwd_settings"
    private const val KEY_API_KEY = "anthropic_api_key"
    private const val KEY_DISABLED_SOURCES = "disabled_sources"

    private val json = Json { ignoreUnknownKeys = true }

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_API_KEY, value.trim()).apply()
    }

    private const val KEY_SKIN = "skin"

    /** The chosen visual skin's enum name; empty until the user picks one. */
    fun getSkinName(context: Context): String =
        prefs(context).getString(KEY_SKIN, "") ?: ""

    fun setSkinName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_SKIN, name).apply()
    }

    private const val KEY_AUTOCHECK_DEFAULT = "autocheck_default"

    /** Whether new puzzles open with real-time error checking already on. */
    fun getAutocheckDefault(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOCHECK_DEFAULT, false)

    fun setAutocheckDefault(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOCHECK_DEFAULT, value).apply()
    }

    private const val KEY_AUTO_DOWNLOAD = "auto_download_prospective"

    /**
     * Whether the twice-daily background refresh should download newly
     * published puzzles from enabled feeds, not just list them.
     */
    fun getAutoDownloadProspective(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DOWNLOAD, false)

    fun setAutoDownloadProspective(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DOWNLOAD, value).apply()
    }

    private const val KEY_CUSTOM_FEEDS = "custom_feeds"

    /** User-added feeds, in the order they were added. */
    fun getCustomFeeds(context: Context): List<CustomFeed> {
        val raw = prefs(context).getString(KEY_CUSTOM_FEEDS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setCustomFeeds(context: Context, feeds: List<CustomFeed>) {
        prefs(context).edit()
            .putString(KEY_CUSTOM_FEEDS, json.encodeToString(feeds))
            .apply()
    }

    private const val KEY_CATALOG_PAGES = "catalog_page_cursors"

    /**
     * Next archive page to list per scraped source, persisted so browsing
     * deeper into an archive resumes where it left off across launches.
     */
    fun getCatalogPageCursors(context: Context): Map<String, Int> =
        (prefs(context).getString(KEY_CATALOG_PAGES, "") ?: "")
            .split(',')
            .mapNotNull { token ->
                val (id, page) = token.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                page.toIntOrNull()?.let { id to it }
            }
            .toMap()

    fun setCatalogPageCursors(context: Context, cursors: Map<String, Int>) {
        prefs(context).edit()
            .putString(KEY_CATALOG_PAGES, cursors.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    /** Source ids the user has toggled off in the library. Empty = all sources on. */
    fun getDisabledSources(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_DISABLED_SOURCES, emptySet()) ?: emptySet()

    fun setDisabledSources(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_DISABLED_SOURCES, ids).apply()
    }

    /** Everything worth carrying to another device, for [Backup]. */
    fun snapshot(context: Context, includeApiKey: Boolean = false): Backup.SettingsSnapshot =
        Backup.SettingsSnapshot(
            skinName = getSkinName(context),
            autocheckDefault = getAutocheckDefault(context),
            autoDownloadProspective = getAutoDownloadProspective(context),
            disabledSources = getDisabledSources(context).sorted(),
            customFeeds = getCustomFeeds(context),
            catalogPageCursors = getCatalogPageCursors(context),
            apiKey = getApiKey(context).takeIf { includeApiKey && it.isNotBlank() },
        )

    /**
     * Write a restored snapshot back. The API key is only touched when the
     * backup carried one, so restoring never clears a key already entered
     * on this device.
     */
    fun apply(context: Context, snapshot: Backup.SettingsSnapshot) {
        setSkinName(context, snapshot.skinName)
        setAutocheckDefault(context, snapshot.autocheckDefault)
        setAutoDownloadProspective(context, snapshot.autoDownloadProspective)
        setDisabledSources(context, snapshot.disabledSources.toSet())
        setCustomFeeds(context, snapshot.customFeeds)
        setCatalogPageCursors(context, snapshot.catalogPageCursors)
        snapshot.apiKey?.takeIf { it.isNotBlank() }?.let { setApiKey(context, it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
