package com.allaway.xwd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allaway.xwd.data.PositionHeatmap
import com.allaway.xwd.data.Stats
import com.allaway.xwd.data.formatSeconds
import com.allaway.xwd.ui.StatsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val stats = viewModel.stats

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Statistics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (stats == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroNumbers(stats) }
            item { SolvingCard(stats) }
            if (stats.averageWhiteCells != null) {
                item { GridCard(stats) }
            }
            if (stats.startHeatmap != null || stats.finishHeatmap != null) {
                item { SolvePathCard(stats) }
            }
            if (stats.solvesByDayOfWeek.any { it > 0 }) {
                item { RhythmCard(stats.solvesByDayOfWeek) }
            }
            if (stats.perSource.isNotEmpty()) {
                item { PerSourceCard(stats) }
            }
        }
    }
}

@Composable
private fun HeroNumbers(stats: Stats) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroNumber(
            value = stats.solvedCount.toString(),
            label = "solved",
            modifier = Modifier.weight(1f),
        )
        HeroNumber(
            value = formatSeconds(stats.averageSeconds),
            label = "average time",
            modifier = Modifier.weight(1f),
        )
        HeroNumber(
            value = stats.bestSeconds?.let { formatSeconds(it) } ?: "–",
            label = "best time",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeroNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SolvingCard(stats: Stats) {
    StatCard("Solving") {
        StatRow("Puzzles downloaded", stats.totalPuzzles.toString())
        StatRow("Puzzles solved", stats.solvedCount.toString())
        StatRow("Clean solves (no help)", stats.cleanSolves.toString())
        StatRow("Total solve time", formatSeconds(stats.totalSolveSeconds))
    }
}

@Composable
private fun GridCard(stats: Stats) {
    StatCard("Grids") {
        if (stats.averageGridWidth != null && stats.averageGridHeight != null) {
            StatRow("Average grid solved", "${stats.averageGridWidth} × ${stats.averageGridHeight}")
        }
        stats.averageWhiteCells?.let { StatRow("Average squares per grid", it.toString()) }
        stats.secondsPerSquare?.let {
            StatRow("Time per square", String.format(Locale.US, "%.1fs", it))
        }
    }
}

@Composable
private fun SolvePathCard(stats: Stats) {
    StatCard("Where you start and finish") {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            stats.startHeatmap?.let { GridHeatmap(it, "First square") }
            stats.finishHeatmap?.let { GridHeatmap(it, "Last square") }
        }
        Spacer(Modifier.height(8.dp))
        val samples = stats.startHeatmap?.samples ?: stats.finishHeatmap?.samples ?: 0
        Text(
            "Where in the grid your solves begin and end, across $samples " +
                if (samples == 1) "solve." else "solves.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A 3x3 map of the grid; darker = more solves start/finish in that region. */
@Composable
private fun GridHeatmap(heatmap: PositionHeatmap, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { c ->
                        val weight = heatmap.weights[r * 3 + c]
                        Box(
                            Modifier
                                .width(34.dp)
                                .aspectRatio(1f)
                                .background(
                                    MaterialTheme.colorScheme.primary
                                        .copy(alpha = 0.07f + 0.86f * weight),
                                    RoundedCornerShape(6.dp),
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RhythmCard(byDay: List<Int>) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val max = byDay.max().coerceAtLeast(1)
    StatCard("Solving rhythm") {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            byDay.forEach { count ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height((6 + 60 * count / max).dp)
                        .background(
                            if (count == 0) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.35f + 0.65f * count / max,
                            ),
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PerSourceCard(stats: Stats) {
    StatCard("By source") {
        stats.perSource.forEachIndexed { i, source ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                source.sourceName,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            StatRow("Solved", source.solved.toString())
            StatRow("Average time", formatSeconds(source.averageSeconds))
            StatRow("Best time", formatSeconds(source.bestSeconds))
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
