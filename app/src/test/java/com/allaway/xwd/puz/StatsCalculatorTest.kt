package com.allaway.xwd.puz

import com.allaway.xwd.data.GridInfo
import com.allaway.xwd.data.PuzzleEntity
import com.allaway.xwd.data.StatsCalculator
import com.allaway.xwd.data.formatSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalculatorTest {

    private fun solved(
        date: LocalDate,
        seconds: Long,
        source: String = "Test",
        firstFill: Int? = null,
        lastFill: Int? = null,
    ) = PuzzleEntity(
        id = "$source-$date",
        sourceId = source.lowercase(),
        sourceName = source,
        date = date.toString(),
        uniqueKey = date.toString(),
        title = "t",
        author = "a",
        puzzleJson = "{}",
        progress = "ABC",
        elapsedSeconds = seconds,
        completedAt = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        firstFillCell = firstFill,
        lastFillCell = lastFill,
        addedAt = 0,
    )

    private val today = LocalDate.of(2026, 6, 10) // a Wednesday

    @Test
    fun computesAggregates() {
        val stats = StatsCalculator.compute(
            totalPuzzles = 5,
            completed = listOf(
                solved(today, 300, "WSJ"),
                solved(today.minusDays(1), 100, "WSJ"),
                solved(today.minusDays(2), 200, "Universal"),
            ),
            zone = ZoneOffset.UTC,
            gridInfo = { null },
        )
        assertEquals(5, stats.totalPuzzles)
        assertEquals(3, stats.solvedCount)
        assertEquals(600, stats.totalSolveSeconds)
        assertEquals(200, stats.averageSeconds)
        assertEquals(100L, stats.bestSeconds)
        assertEquals(2, stats.perSource.size)
        assertEquals("WSJ", stats.perSource[0].sourceName)
        assertEquals(100, stats.perSource[0].bestSeconds)
        // No grid info available: size and position stats stay empty.
        assertNull(stats.averageGridWidth)
        assertNull(stats.secondsPerSquare)
        assertNull(stats.startHeatmap)
    }

    @Test
    fun computesGridSizeStats() {
        val grid = GridInfo(width = 15, height = 15, whiteCells = 180)
        val stats = StatsCalculator.compute(
            totalPuzzles = 2,
            completed = listOf(solved(today, 360), solved(today.minusDays(1), 540)),
            zone = ZoneOffset.UTC,
            gridInfo = { grid },
        )
        assertEquals(15, stats.averageGridWidth)
        assertEquals(15, stats.averageGridHeight)
        assertEquals(180, stats.averageWhiteCells)
        assertEquals(2.5, stats.secondsPerSquare!!, 0.001) // (2 + 3) / 2
    }

    @Test
    fun heatmapsTrackFirstAndLastFill() {
        val grid = GridInfo(width = 15, height = 15, whiteCells = 180)
        val stats = StatsCalculator.compute(
            totalPuzzles = 2,
            completed = listOf(
                // Starts top-left (cell 0), finishes bottom-right (last cell).
                solved(today, 100, firstFill = 0, lastFill = 224),
                solved(today.minusDays(1), 100, firstFill = 16, lastFill = 224),
            ),
            zone = ZoneOffset.UTC,
            gridInfo = { grid },
        )
        val start = stats.startHeatmap!!
        assertEquals(2, start.samples)
        assertEquals(1f, start.weights[0]) // both starts in the top-left region
        assertEquals(0f, start.weights[8])
        val finish = stats.finishHeatmap!!
        assertEquals(1f, finish.weights[8]) // both finishes bottom-right
        assertEquals(0f, finish.weights[0])
    }

    @Test
    fun heatmapNullWithoutFillData() {
        val stats = StatsCalculator.compute(
            totalPuzzles = 1,
            completed = listOf(solved(today, 100)),
            zone = ZoneOffset.UTC,
            gridInfo = { GridInfo(15, 15, 180) },
        )
        assertNull(stats.startHeatmap)
        assertNull(stats.finishHeatmap)
    }

    @Test
    fun regionsMapCellsToNinths() {
        // 15x15 grid: rows/cols 0-4 -> band 0, 5-9 -> band 1, 10-14 -> band 2.
        assertEquals(0, StatsCalculator.region(0, 15, 15))
        assertEquals(4, StatsCalculator.region(7 * 15 + 7, 15, 15))
        assertEquals(8, StatsCalculator.region(224, 15, 15))
        assertEquals(2, StatsCalculator.region(14, 15, 15))
        assertEquals(6, StatsCalculator.region(14 * 15, 15, 15))
    }

    @Test
    fun countsSolvesByDayOfWeek() {
        val stats = StatsCalculator.compute(
            totalPuzzles = 3,
            completed = listOf(
                solved(today, 100), // Wednesday
                solved(today.minusDays(1), 100), // Tuesday
                solved(today.minusDays(7), 100), // Wednesday
            ),
            zone = ZoneOffset.UTC,
            gridInfo = { null },
        )
        assertEquals(listOf(0, 1, 2, 0, 0, 0, 0), stats.solvesByDayOfWeek)
    }

    @Test
    fun cleanSolvesExcludeAssisted() {
        val assisted = solved(today, 100).copy(autocheckUsed = true)
        val clean = solved(today.minusDays(1), 100)
        val stats = StatsCalculator.compute(2, listOf(assisted, clean), ZoneOffset.UTC) { null }
        assertEquals(1, stats.cleanSolves)
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:42", formatSeconds(42))
        assertEquals("12:05", formatSeconds(725))
        assertEquals("1:00:01", formatSeconds(3601))
    }
}
