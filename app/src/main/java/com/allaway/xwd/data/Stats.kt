package com.allaway.xwd.data

import com.allaway.xwd.model.Puzzle
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
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
 * Where in the grid solves tend to start or finish: a 3x3 map of the grid
 * (top-left .. bottom-right), each cell weighted 0..1 relative to the most
 * common region.
 */
data class PositionHeatmap(
    val weights: List<Float>,
    val samples: Int,
)

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
    val startHeatmap: PositionHeatmap?,
    val finishHeatmap: PositionHeatmap?,
    /** Completed-puzzle counts Monday..Sunday. */
    val solvesByDayOfWeek: List<Int>,
    val perSource: List<SourceStats>,
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

        val byDay = IntArray(7)
        completed.mapNotNull { it.completedAt }
            .map { Instant.ofEpochMilli(it).atZone(zone).dayOfWeek }
            .forEach { byDay[it.ordinal - DayOfWeek.MONDAY.ordinal]++ }

        val solvedWithTime = withGrid.filter { (e, g) -> e.elapsedSeconds > 0 && g.whiteCells > 0 }

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
            startHeatmap = heatmap(withGrid) { it.firstFillCell },
            finishHeatmap = heatmap(withGrid) { it.lastFillCell },
            solvesByDayOfWeek = byDay.toList(),
            perSource = completed.groupBy { it.sourceName }.map { (name, list) ->
                SourceStats(
                    sourceName = name,
                    solved = list.size,
                    averageSeconds = list.sumOf { it.elapsedSeconds } / list.size,
                    bestSeconds = list.minOf { it.elapsedSeconds },
                )
            }.sortedByDescending { it.solved },
        )
    }

    /** 3x3 region (row-major, 0..8) of a cell within a width x height grid. */
    fun region(cell: Int, width: Int, height: Int): Int {
        val row = cell / width
        val col = cell % width
        val r = (row * 3 / height).coerceIn(0, 2)
        val c = (col * 3 / width).coerceIn(0, 2)
        return r * 3 + c
    }

    private fun heatmap(
        withGrid: List<Pair<PuzzleEntity, GridInfo>>,
        cellOf: (PuzzleEntity) -> Int?,
    ): PositionHeatmap? {
        val counts = IntArray(9)
        var samples = 0
        for ((entity, grid) in withGrid) {
            val cell = cellOf(entity) ?: continue
            if (cell !in 0 until grid.width * grid.height) continue
            counts[region(cell, grid.width, grid.height)]++
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
