package app.xwd.puz

import app.xwd.data.GridInfo
import app.xwd.data.PuzzleEntity
import app.xwd.data.StatsCalculator
import app.xwd.data.formatSeconds
import app.xwd.model.SizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertTrue(stats.heatmapsBySize.isEmpty())
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
    fun heatmapsTrackFirstAndLastFillPerSize() {
        // A 15x15 is a Maxi, so its heatmap is 7x7, not 3x3.
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
        val maps = stats.heatmapsBySize.single()
        assertEquals(SizeClass.MAXI, maps.sizeClass)
        assertEquals(7, maps.resolution)
        assertEquals(2, maps.samples)
        val start = maps.start!!
        assertEquals(49, start.weights.size)
        assertEquals(1f, start.weights[0]) // both starts in the top-left region
        assertEquals(0f, start.weights[48])
        val finish = maps.finish!!
        assertEquals(1f, finish.weights[48]) // both finishes bottom-right
        assertEquals(0f, finish.weights[0])
    }

    @Test
    fun heatmapsSplitBySizeClassAndSkipSizesWithoutData() {
        val mini = GridInfo(width = 5, height = 5, whiteCells = 25)
        val maxi = GridInfo(width = 15, height = 15, whiteCells = 180)
        val stats = StatsCalculator.compute(
            totalPuzzles = 3,
            completed = listOf(
                solved(today, 60, source = "Mini", firstFill = 0, lastFill = 24),
                solved(today.minusDays(1), 600, source = "Maxi", firstFill = 0, lastFill = 224),
                // A midi solve with no fill data records no heatmap at all.
                solved(today.minusDays(2), 300, source = "Midi"),
            ),
            zone = ZoneOffset.UTC,
            gridInfo = {
                when (it.sourceName) {
                    "Mini" -> mini
                    "Maxi" -> maxi
                    else -> GridInfo(11, 11, 100)
                }
            },
        )
        assertEquals(listOf(SizeClass.MINI, SizeClass.MAXI), stats.heatmapsBySize.map { it.sizeClass })
        assertEquals(listOf(3, 7), stats.heatmapsBySize.map { it.resolution })
        assertEquals(9, stats.heatmapsBySize[0].start!!.weights.size)
    }

    @Test
    fun heatmapAbsentWithoutFillData() {
        val stats = StatsCalculator.compute(
            totalPuzzles = 1,
            completed = listOf(solved(today, 100)),
            zone = ZoneOffset.UTC,
            gridInfo = { GridInfo(15, 15, 180) },
        )
        assertTrue(stats.heatmapsBySize.isEmpty())
    }

    @Test
    fun regionsMapCellsAtAnyResolution() {
        // 15x15 at resolution 3: rows/cols 0-4 -> band 0, 5-9 -> 1, 10-14 -> 2.
        assertEquals(0, StatsCalculator.region(0, 15, 15))
        assertEquals(4, StatsCalculator.region(7 * 15 + 7, 15, 15))
        assertEquals(8, StatsCalculator.region(224, 15, 15))
        assertEquals(2, StatsCalculator.region(14, 15, 15))
        assertEquals(6, StatsCalculator.region(14 * 15, 15, 15))
        // At resolution 7 the same 15x15 maps corner-to-corner across 49 cells.
        assertEquals(0, StatsCalculator.region(0, 15, 15, 7))
        assertEquals(48, StatsCalculator.region(224, 15, 15, 7))
        assertEquals(24, StatsCalculator.region(7 * 15 + 7, 15, 15, 7)) // dead center
    }

    @Test
    fun heatResolutionGrowsWithSizeClass() {
        assertEquals(3, StatsCalculator.heatResolution(SizeClass.MINI))
        assertEquals(5, StatsCalculator.heatResolution(SizeClass.MIDI))
        assertEquals(7, StatsCalculator.heatResolution(SizeClass.MAXI))
        assertEquals(9, StatsCalculator.heatResolution(SizeClass.SUPERMAXI))
        assertEquals(11, StatsCalculator.heatResolution(SizeClass.ULTRAMAXI))
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
