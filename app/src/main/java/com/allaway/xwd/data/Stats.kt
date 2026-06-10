package com.allaway.xwd.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SourceStats(
    val sourceName: String,
    val solved: Int,
    val averageSeconds: Long,
    val bestSeconds: Long,
)

data class Stats(
    val totalPuzzles: Int,
    val solvedCount: Int,
    val totalSolveSeconds: Long,
    val averageSeconds: Long,
    val bestSeconds: Long?,
    /** Solved without autocheck, checks, or reveals. */
    val cleanSolves: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val perSource: List<SourceStats>,
)

object StatsCalculator {

    fun compute(
        totalPuzzles: Int,
        completed: List<PuzzleEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Stats {
        val times = completed.map { it.elapsedSeconds }
        val solveDays = completed.mapNotNull { it.completedAt }
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .toSortedSet()

        return Stats(
            totalPuzzles = totalPuzzles,
            solvedCount = completed.size,
            totalSolveSeconds = times.sum(),
            averageSeconds = if (times.isEmpty()) 0 else times.sum() / times.size,
            bestSeconds = times.minOrNull(),
            cleanSolves = completed.count { !it.autocheckUsed && it.revealCount == 0 && it.checkCount == 0 },
            currentStreakDays = currentStreak(solveDays, today),
            longestStreakDays = longestStreak(solveDays),
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

    /** Consecutive days with a solve, counting back from today (or yesterday). */
    fun currentStreak(solveDays: Collection<LocalDate>, today: LocalDate): Int {
        val days = solveDays.toHashSet()
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(solveDays: Collection<LocalDate>): Int {
        val sorted = solveDays.toSortedSet()
        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in sorted) {
            run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
            if (run > longest) longest = run
            prev = day
        }
        return longest
    }
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
