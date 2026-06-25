package app.xwd.data

import app.xwd.model.Puzzle
import app.xwd.model.SizeClass
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SourceStats(
    val sourceName: String,
    val solved: Int,
    val averageSeconds: Long,
    val bestSeconds: Long,
)

/** Grid dimensions of a stored puzzle, needed for size and position stats. */
data class GridInfo(
    val width: Int,
    val height: Int,
    val whiteCells: Int,
)

/**
 * Where in the grid solves tend to start or finish: a resolution x resolution
 * map of the grid (top-left .. bottom-right, row-major), each cell weighted
 * 0..1 relative to the most common region.
 */
data class PositionHeatmap(
    val weights: List<Float>,
    val samples: Int,
)

/**
 * Start/finish heatmaps for one grid size class. Bigger grids get finer
 * heatmaps so a Maxi's solve paths aren't squashed into a Mini's 3x3.
 */
data class SizeHeatmaps(
    val sizeClass: SizeClass,
    val resolution: Int,
    val start: PositionHeatmap?,
    val finish: PositionHeatmap?,
) {
    val samples: Int get() = maxOf(start?.samples ?: 0, finish?.samples ?: 0)
}

data class Stats(
    val totalPuzzles: Int,
    val solvedCount: Int,
    val totalSolveSeconds: Long,
    val averageSeconds: Long,
    val bestSeconds: Long?,
    /** Solved without autocheck, checks, or reveals. */
    val cleanSolves: Int,
    /** Average dimensions of completed grids, null until something is solved. */
    val averageGridWidth: Int?,
    val averageGridHeight: Int?,
    val averageWhiteCells: Int?,
    /** Average seconds spent per white square across completed puzzles. */
    val secondsPerSquare: Double?,
    /** One start/finish heatmap pair per size class that has solves. */
    val heatmapsBySize: List<SizeHeatmaps>,
    /** Completed-puzzle counts Monday..Sunday. */
    val solvesByDayOfWeek: List<Int>,
    val perSource: List<SourceStats>,
    /** When the first recorded solve happened, for "since ..." footers. */
    val firstSolveEpochMillis: Long?,
    /** Days solved in a row ending today (or yesterday if none today). */
    val currentStreak: Int,
    /** Longest ever consecutive-day solve run. */
    val longestStreak: Int,
    /** Fastest solve time per grid size class, only for puzzles with known grid info. */
    val bestBySize: Map<SizeClass, Long>,
)

object StatsCalculator {

    private val json = Json { ignoreUnknownKeys = true }

    /** Grid dimensions decoded from the stored puzzle JSON; null if undecodable. */
    fun gridInfoFromJson(entity: PuzzleEntity): GridInfo? = try {
        val puzzle = json.decodeFromString<Puzzle>(entity.puzzleJson)
        GridInfo(puzzle.width, puzzle.height, puzzle.cells.count { !it.isBlock })
    } catch (_: Exception) {
        null
    }

    fun compute(
        totalPuzzles: Int,
        completed: List<PuzzleEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        gridInfo: (PuzzleEntity) -> GridInfo? = ::gridInfoFromJson,
    ): Stats {
        val times = completed.map { it.elapsedSeconds }
        val withGrid = completed.mapNotNull { e -> gridInfo(e)?.let { e to it } }

        val completionDates = completed.mapNotNull { it.completedAt }
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val byDay = IntArray(7)
        completionDates.forEach { date ->
            byDay[date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal]++
        }
        // Unique days for streak calculation (a day counts once no matter how many puzzles were solved).
        val solvedDates = completionDates.toSortedSet()

        val solvedWithTime = withGrid.filter { (e, g) -> e.elapsedSeconds > 0 && g.whiteCells > 0 }

        // Streak: consecutive solve days ending today or yesterday.
        val today = LocalDate.now(zone)
        var currentStreak = 0
        var d = if (today in solvedDates) today else today.minusDays(1)
        while (d in solvedDates) { currentStreak++; d = d.minusDays(1) }

        // Longest ever consecutive-day streak.
        var longestStreak = 0
        var run = 0
        var prev: LocalDate? = null
        for (date in solvedDates) {
            run = if (prev != null && date == prev!!.plusDays(1)) run + 1 else 1
            longestStreak = maxOf(longestStreak, run)
            prev = date
        }

        return Stats(
            totalPuzzles = totalPuzzles,
            solvedCount = completed.size,
            totalSolveSeconds = times.sum(),
            averageSeconds = if (times.isEmpty()) 0 else times.sum() / times.size,
            bestSeconds = times.minOrNull(),
            cleanSolves = completed.count { !it.autocheckUsed && it.revealCount == 0 && it.checkCount == 0 },
            averageGridWidth = withGrid.map { it.second.width }.averageOrNull(),
            averageGridHeight = withGrid.map { it.second.height }.averageOrNull(),
            averageWhiteCells = withGrid.map { it.second.whiteCells }.averageOrNull(),
            secondsPerSquare = solvedWithTime
                .map { (e, g) -> e.elapsedSeconds.toDouble() / g.whiteCells }
                .ifEmpty { null }?.average(),
            heatmapsBySize = heatmapsBySize(withGrid),
            solvesByDayOfWeek = byDay.toList(),
            firstSolveEpochMillis = completed.mapNotNull { it.completedAt }.minOrNull(),
            perSource = completed.groupBy { it.sourceName }.map { (name, list) ->
                SourceStats(
                    sourceName = name,
                    solved = list.size,
                    averageSeconds = list.sumOf { it.elapsedSeconds } / list.size,
                    bestSeconds = list.minOf { it.elapsedSeconds },
                )
            }.sortedByDescending { it.solved },
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            bestBySize = withGrid
                .filter { (e, _) -> e.elapsedSeconds > 0 }
                .groupBy { (_, g) -> SizeClass.forDimensions(g.width, g.height) }
                .mapValues { (_, list) -> list.minOf { (e, _) -> e.elapsedSeconds } },
        )
    }

    /**
     * Heatmap resolution per size class: finer for bigger grids, but always
     * coarser than the grid itself so the map reads as regions, not cells.
     */
    fun heatResolution(size: SizeClass): Int = when (size) {
        SizeClass.MINI -> 3
        SizeClass.MIDI -> 5
        SizeClass.MAXI -> 7
        SizeClass.SUPERMAXI -> 9
        SizeClass.ULTRAMAXI -> 11
    }

    /** Region index (row-major) of a cell within a width x height grid. */
    fun region(cell: Int, width: Int, height: Int, resolution: Int = 3): Int {
        val row = cell / width
        val col = cell % width
        val r = (row * resolution / height).coerceIn(0, resolution - 1)
        val c = (col * resolution / width).coerceIn(0, resolution - 1)
        return r * resolution + c
    }

    /** One start/finish heatmap pair per size class with position data. */
    private fun heatmapsBySize(withGrid: List<Pair<PuzzleEntity, GridInfo>>): List<SizeHeatmaps> =
        withGrid
            .groupBy { (_, g) -> SizeClass.forDimensions(g.width, g.height) }
            .mapNotNull { (size, group) ->
                val resolution = heatResolution(size)
                val start = heatmap(group, resolution) { it.firstFillCell }
                val finish = heatmap(group, resolution) { it.lastFillCell }
                if (start == null && finish == null) null
                else SizeHeatmaps(size, resolution, start, finish)
            }
            .sortedBy { it.sizeClass }

    private fun heatmap(
        withGrid: List<Pair<PuzzleEntity, GridInfo>>,
        resolution: Int,
        cellOf: (PuzzleEntity) -> Int?,
    ): PositionHeatmap? {
        val counts = IntArray(resolution * resolution)
        var samples = 0
        for ((entity, grid) in withGrid) {
            val cell = cellOf(entity) ?: continue
            if (cell !in 0 until grid.width * grid.height) continue
            counts[region(cell, grid.width, grid.height, resolution)]++
            samples++
        }
        if (samples == 0) return null
        val max = counts.max().toFloat()
        return PositionHeatmap(counts.map { it / max }, samples)
    }

    private fun List<Int>.averageOrNull(): Int? =
        if (isEmpty()) null else Math.round(average()).toInt()
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
