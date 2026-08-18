package app.xwd.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What an export wrote. */
data class ExportResult(val puzzles: Int, val catalog: Int, val bytes: Long)

/** What a restore took in. */
data class RestoreResult(
    val puzzlesAdded: Int,
    val puzzlesUpdated: Int,
    val puzzlesKept: Int,
    val catalogAdded: Int,
    val settingsRestored: Boolean,
    val apiKeyRestored: Boolean,
)

/**
 * Moves a solver's whole xwd life — downloaded puzzles, solving progress and
 * times, the catalog of what each feed has published, and settings — between
 * devices as a single file.
 *
 * Both directions stream in pages, so an archive of thousands of puzzles
 * never has to sit in memory at once, and restoring merges rather than
 * overwrites (see [Backup.merge]), so restoring the same file onto a phone
 * that has kept solving can't lose a solve.
 */
class BackupRepository(
    private val context: Context,
    private val db: XwdDatabase = XwdDatabase.get(context),
) {
    private val puzzleDao = db.puzzleDao()
    private val catalogDao = db.catalogDao()

    /**
     * Write the whole database and settings to [out] as a zip. [onProgress]
     * reports rows written so far against the total to expect.
     */
    suspend fun export(
        out: OutputStream,
        includeApiKey: Boolean,
        appVersionName: String = "",
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportResult = withContext(Dispatchers.IO) {
        val puzzleTotal = puzzleDao.count()
        val catalogTotal = catalogDao.count()
        val total = puzzleTotal + catalogTotal
        var done = 0
        val counting = CountingOutputStream(out)

        ZipOutputStream(counting.buffered()).use { zip ->
            zip.entry(Backup.ENTRY_MANIFEST) { writer ->
                writer.write(
                    Backup.json.encodeToString(
                        Backup.Manifest.serializer(),
                        Backup.Manifest(
                            createdAt = System.currentTimeMillis(),
                            appVersionName = appVersionName,
                            puzzleCount = puzzleTotal,
                            catalogCount = catalogTotal,
                            includesApiKey = includeApiKey,
                        ),
                    ),
                )
            }

            zip.entry(Backup.ENTRY_SETTINGS) { writer ->
                writer.write(
                    Backup.json.encodeToString(
                        Backup.SettingsSnapshot.serializer(),
                        Settings.snapshot(context, includeApiKey),
                    ),
                )
            }

            zip.entry(Backup.ENTRY_PUZZLES) { writer ->
                var after = ""
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val page = puzzleDao.pageAfter(after, PAGE)
                    if (page.isEmpty()) break
                    page.forEach { writer.appendLine(Backup.encodePuzzle(it)) }
                    after = page.last().id
                    done += page.size
                    onProgress(done, total)
                }
            }

            zip.entry(Backup.ENTRY_CATALOG) { writer ->
                var after = ""
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val page = catalogDao.pageAfter(after, PAGE)
                    if (page.isEmpty()) break
                    page.forEach { writer.appendLine(Backup.encodeCatalog(it)) }
                    after = page.last().id
                    done += page.size
                    onProgress(done, total)
                }
            }
        }
        ExportResult(puzzles = puzzleTotal, catalog = catalogTotal, bytes = counting.count)
    }

    /**
     * Read a backup from [input] and merge it into this device. [onProgress]
     * reports rows handled so far against the manifest's totals.
     */
    suspend fun restore(
        input: InputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): RestoreResult = withContext(Dispatchers.IO) {
        var manifest: Backup.Manifest? = null
        var settings: Backup.SettingsSnapshot? = null
        var added = 0
        var updated = 0
        var kept = 0
        var catalogAdded = 0
        var done = 0

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val total = manifest?.let { it.puzzleCount + it.catalogCount } ?: 0
                when (entry.name.substringAfterLast('/')) {
                    Backup.ENTRY_MANIFEST -> {
                        manifest = runCatching {
                            Backup.json.decodeFromString(
                                Backup.Manifest.serializer(),
                                zip.readBytes().decodeToString(),
                            )
                        }.getOrNull()
                        // Fail before a single row is written if this isn't ours.
                        Backup.validate(manifest)
                    }
                    Backup.ENTRY_SETTINGS -> {
                        Backup.validate(manifest)
                        settings = runCatching {
                            Backup.json.decodeFromString(
                                Backup.SettingsSnapshot.serializer(),
                                zip.readBytes().decodeToString(),
                            )
                        }.getOrNull()
                    }
                    Backup.ENTRY_PUZZLES -> {
                        Backup.validate(manifest)
                        zip.forEachBatch { batch ->
                            val rows = batch.mapNotNull { line ->
                                runCatching { Backup.decodePuzzle(line) }.getOrNull()
                            }
                            val existing = puzzleDao.getMany(rows.map { it.id }).associateBy { it.id }
                            val merged = rows.mapNotNull { incoming ->
                                val local = existing[incoming.id]
                                val winner = Backup.merge(local, incoming)
                                when {
                                    local == null -> winner.also { added++ }
                                    winner == local -> null.also { kept++ }
                                    else -> winner.also { updated++ }
                                }
                            }
                            if (merged.isNotEmpty()) puzzleDao.upsertAll(merged)
                            done += batch.size
                            onProgress(done, total)
                        }
                    }
                    Backup.ENTRY_CATALOG -> {
                        Backup.validate(manifest)
                        zip.forEachBatch { batch ->
                            val rows = batch.mapNotNull { line ->
                                runCatching { Backup.decodeCatalog(line) }.getOrNull()
                            }
                            // Catalog rows are fixed once discovered, so rows
                            // already listed here are left exactly as they are.
                            val known = catalogDao.knownIds(rows.map { it.id }).toSet()
                            catalogDao.insertAll(rows)
                            catalogAdded += rows.count { it.id !in known }
                            done += batch.size
                            onProgress(done, total)
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        // A zip with no manifest at all never reached a validate() above.
        Backup.validate(manifest)

        val restored = settings
        if (restored != null) {
            Settings.apply(
                context,
                Backup.mergeSettings(
                    local = Settings.snapshot(context, includeApiKey = true),
                    incoming = restored,
                ),
            )
        }
        RestoreResult(
            puzzlesAdded = added,
            puzzlesUpdated = updated,
            puzzlesKept = kept,
            catalogAdded = catalogAdded,
            settingsRestored = restored != null,
            apiKeyRestored = restored?.apiKey.isNullOrBlank().not(),
        )
    }

    /** Reads a JSONL entry line by line, handing over [BATCH] rows at a time. */
    private suspend fun ZipInputStream.forEachBatch(block: suspend (List<String>) -> Unit) {
        // Wrapped so the reader can't close the zip: more entries follow.
        val reader = BufferedReader(InputStreamReader(NonClosingInputStream(this), Charsets.UTF_8))
        val batch = ArrayList<String>(BATCH)
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            batch += line
            if (batch.size == BATCH) {
                block(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) block(batch.toList())
    }

    /** Runs [body] against a writer for one zip entry, flushing before it closes. */
    private inline fun ZipOutputStream.entry(name: String, body: (Writer) -> Unit) {
        putNextEntry(ZipEntry(name))
        val writer = OutputStreamWriter(NonClosingOutputStream(this), Charsets.UTF_8)
        body(writer)
        writer.flush()
        closeEntry()
    }

    private class NonClosingInputStream(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

        override fun available(): Int = delegate.available()

        override fun close() = Unit
    }

    private class NonClosingOutputStream(delegate: OutputStream) : FilterOutputStream(delegate) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

        override fun close() = flush()
    }

    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            count += len
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    private companion object {
        /** Rows read from the database per page when exporting. */
        const val PAGE = 100

        /** Rows merged per batch when restoring. */
        const val BATCH = 100
    }
}
