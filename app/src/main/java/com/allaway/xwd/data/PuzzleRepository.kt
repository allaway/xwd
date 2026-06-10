package com.allaway.xwd.data

import com.allaway.xwd.model.Puzzle
import com.allaway.xwd.sources.PuzzleDownloader
import com.allaway.xwd.sources.PuzzleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.LocalDate

class PuzzleRepository(
    private val dao: PuzzleDao,
    private val downloader: PuzzleDownloader = PuzzleDownloader(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<PuzzleEntity>> = dao.observeAll()

    suspend fun get(id: String): PuzzleEntity? = dao.get(id)

    suspend fun update(entity: PuzzleEntity) = dao.update(entity)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun completed(): List<PuzzleEntity> = dao.completed()

    suspend fun totalCount(): Int = dao.count()

    fun decode(entity: PuzzleEntity): Puzzle = json.decodeFromString(entity.puzzleJson)

    /** Download the newest not-yet-stored puzzle from [source]. Returns it, or null if up to date. */
    suspend fun downloadLatest(source: PuzzleSource): PuzzleEntity? {
        val have = dao.datesForSource(source.id).toSet()
        val downloaded = downloader.fetchLatest(source, have) ?: return null
        return store(source, downloaded.puzzle, downloaded.date)
    }

    /** Download the puzzle for a specific date. Returns null if the feed has none. */
    suspend fun downloadFor(source: PuzzleSource, date: LocalDate): PuzzleEntity? {
        val existingId = "${source.id}-$date"
        dao.get(existingId)?.let { return it }
        val downloaded = downloader.fetch(source, date) ?: return null
        return store(source, downloaded.puzzle, downloaded.date)
    }

    private suspend fun store(source: PuzzleSource, puzzle: Puzzle, date: LocalDate): PuzzleEntity {
        val emptyProgress = buildString {
            puzzle.cells.forEach { append(if (it.isBlock) '.' else '-') }
        }
        val entity = PuzzleEntity(
            id = "${source.id}-$date",
            sourceId = source.id,
            sourceName = source.name,
            date = date.toString(),
            title = puzzle.title.ifBlank { "${source.name} $date" },
            author = puzzle.author,
            puzzleJson = json.encodeToString(Puzzle.serializer(), puzzle),
            progress = emptyProgress,
            addedAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
        return entity
    }
}
