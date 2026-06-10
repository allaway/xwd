package com.allaway.xwd.puz

import com.allaway.xwd.data.PuzzleEntity
import com.allaway.xwd.data.StatsCalculator
import com.allaway.xwd.data.formatSeconds
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalculatorTest {

    private fun solved(date: LocalDate, seconds: Long, source: String = "Test") = PuzzleEntity(
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
        addedAt = 0,
    )

    private val today = LocalDate.of(2026, 6, 10)

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
            today = today,
        )
        assertEquals(5, stats.totalPuzzles)
        assertEquals(3, stats.solvedCount)
        assertEquals(600, stats.totalSolveSeconds)
        assertEquals(200, stats.averageSeconds)
        assertEquals(100L, stats.bestSeconds)
        assertEquals(3, stats.currentStreakDays)
        assertEquals(3, stats.longestStreakDays)
        assertEquals(2, stats.perSource.size)
        assertEquals("WSJ", stats.perSource[0].sourceName)
        assertEquals(100, stats.perSource[0].bestSeconds)
    }

    @Test
    fun currentStreakBrokenByGap() {
        val days = listOf(today.minusDays(3), today.minusDays(4))
        assertEquals(0, StatsCalculator.currentStreak(days, today))
        assertEquals(2, StatsCalculator.longestStreak(days))
    }

    @Test
    fun streakCountsFromYesterday() {
        val days = listOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, StatsCalculator.currentStreak(days, today))
    }

    @Test
    fun cleanSolvesExcludeAssisted() {
        val assisted = solved(today, 100).copy(autocheckUsed = true)
        val clean = solved(today.minusDays(1), 100)
        val stats = StatsCalculator.compute(2, listOf(assisted, clean), ZoneOffset.UTC, today)
        assertEquals(1, stats.cleanSolves)
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:42", formatSeconds(42))
        assertEquals("12:05", formatSeconds(725))
        assertEquals("1:00:01", formatSeconds(3601))
    }
}
