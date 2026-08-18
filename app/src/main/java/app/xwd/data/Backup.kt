package app.xwd.data

import app.xwd.sources.CustomFeed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The transfer file: everything one device knows, in a form another device
 * can absorb. Written as a zip so a full archive of puzzles (each carrying
 * its own puzzle JSON) stays a manageable download/upload, and so the
 * pieces can be streamed in and out rather than held in memory at once.
 *
 * Pure JVM on purpose — the format and the merge rules are unit-tested
 * without an emulator; [BackupRepository] does the Android-side I/O.
 */
object Backup {

    /** Bumped only for changes older readers can't understand. */
    const val FORMAT_VERSION = 1

    const val MIME_TYPE = "application/zip"

    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_SETTINGS = "settings.json"
    const val ENTRY_PUZZLES = "puzzles.jsonl"
    const val ENTRY_CATALOG = "catalog.jsonl"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Identifies the file as an xwd backup and says how much is inside. */
    @Serializable
    data class Manifest(
        val app: String = APP_MARKER,
        val formatVersion: Int = FORMAT_VERSION,
        val createdAt: Long = 0,
        val appVersionName: String = "",
        val puzzleCount: Int = 0,
        val catalogCount: Int = 0,
        val includesApiKey: Boolean = false,
    )

    const val APP_MARKER = "xwd"

    /** Everything in the preferences store that is worth carrying over. */
    @Serializable
    data class SettingsSnapshot(
        val skinName: String = "",
        val autocheckDefault: Boolean = false,
        val autoDownloadProspective: Boolean = false,
        val disabledSources: List<String> = emptyList(),
        val customFeeds: List<CustomFeed> = emptyList(),
        val catalogPageCursors: Map<String, Int> = emptyMap(),
        /** Only present when the user opted in when exporting. */
        val apiKey: String? = null,
    )

    /** Why a file couldn't be read as a backup. Surfaced verbatim to the user. */
    class InvalidBackupException(message: String) : Exception(message)

    /**
     * Checks a manifest before anything is written to the database. Throws
     * [InvalidBackupException] when the file isn't ours or was written by a
     * newer xwd than this one.
     */
    fun validate(manifest: Manifest?) {
        if (manifest == null || manifest.app != APP_MARKER) {
            throw InvalidBackupException("That file isn't an xwd backup.")
        }
        if (manifest.formatVersion > FORMAT_VERSION) {
            throw InvalidBackupException(
                "That backup was made by a newer version of xwd. Update the app, then restore again.",
            )
        }
    }

    /** Default name for the exported file, e.g. `xwd-backup-2026-08-17.zip`. */
    fun fileName(isoDate: String): String = "xwd-backup-$isoDate.zip"

    /**
     * How much solving a saved row represents. Used to decide which copy of
     * the same puzzle wins when both devices have worked on it: finishing
     * beats any amount of partial fill, more filled squares beat fewer, and
     * time on the clock breaks the remaining ties.
     */
    fun progressScore(row: PuzzleEntity): Triple<Int, Int, Long> = Triple(
        if (row.isCompleted) 1 else 0,
        row.filledCount,
        row.elapsedSeconds,
    )

    private val scoreOrder = compareBy<Triple<Int, Int, Long>>({ it.first }, { it.second }, { it.third })

    /**
     * Merge one puzzle from the backup with what's already on this device.
     *
     * A fresh phone has no local row, so the backup's copy is taken as-is.
     * When both exist the more-progressed copy wins ([progressScore]), with
     * a tie going to the device being restored onto so a restore can never
     * throw away work it can't prove is stale. Either way the row keeps the
     * earliest `addedAt` of the two, so "when did I get this puzzle" stays
     * true across the move.
     */
    fun merge(local: PuzzleEntity?, incoming: PuzzleEntity): PuzzleEntity {
        if (local == null) return incoming
        val winner = if (scoreOrder.compare(progressScore(incoming), progressScore(local)) > 0) {
            incoming
        } else {
            local
        }
        return winner.copy(addedAt = minOf(local.addedAt, incoming.addedAt))
    }

    /**
     * Merge the backup's settings into this device's. The backup is the
     * user's intent — it wins for the skin, the solving defaults, and which
     * sources are switched off — but custom feeds are unioned so feeds added
     * on the new phone before restoring aren't dropped, and page cursors keep
     * whichever device had read deeper into an archive.
     */
    fun mergeSettings(local: SettingsSnapshot, incoming: SettingsSnapshot): SettingsSnapshot {
        val feeds = local.customFeeds.toMutableList()
        val seen = local.customFeeds.mapTo(mutableSetOf()) { normalizeUrl(it.pageUrl) }
        incoming.customFeeds.forEach { feed ->
            if (seen.add(normalizeUrl(feed.pageUrl))) feeds += feed
        }
        val cursors = local.catalogPageCursors.toMutableMap()
        incoming.catalogPageCursors.forEach { (id, page) ->
            cursors[id] = maxOf(cursors[id] ?: 0, page)
        }
        // Feeds that only exist locally keep their own on/off state; the rest
        // follow the backup.
        val localOnlyIds = local.customFeeds.map { it.id }.toSet() -
            incoming.customFeeds.map { it.id }.toSet()
        val disabled = incoming.disabledSources.toMutableSet()
        disabled += local.disabledSources.filter { it in localOnlyIds }
        return SettingsSnapshot(
            skinName = incoming.skinName.ifBlank { local.skinName },
            autocheckDefault = incoming.autocheckDefault,
            autoDownloadProspective = incoming.autoDownloadProspective,
            disabledSources = disabled.toList().sorted(),
            customFeeds = feeds,
            catalogPageCursors = cursors,
            apiKey = incoming.apiKey?.takeIf { it.isNotBlank() } ?: local.apiKey,
        )
    }

    /** Same feed typed twice — different case, scheme, or trailing slash — is one feed. */
    private fun normalizeUrl(url: String): String =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')

    fun encodePuzzle(row: PuzzleEntity): String = json.encodeToString(PuzzleEntity.serializer(), row)

    fun decodePuzzle(line: String): PuzzleEntity = json.decodeFromString(PuzzleEntity.serializer(), line)

    fun encodeCatalog(row: CatalogEntity): String = json.encodeToString(CatalogEntity.serializer(), row)

    fun decodeCatalog(line: String): CatalogEntity = json.decodeFromString(CatalogEntity.serializer(), line)
}
