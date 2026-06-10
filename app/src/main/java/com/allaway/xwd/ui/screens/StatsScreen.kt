package com.allaway.xwd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allaway.xwd.data.formatSeconds
import com.allaway.xwd.ui.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val stats = viewModel.stats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Solving", style = MaterialTheme.typography.titleMedium)
                        StatRow("Puzzles downloaded", stats.totalPuzzles.toString())
                        StatRow("Puzzles solved", stats.solvedCount.toString())
                        StatRow("Clean solves (no help)", stats.cleanSolves.toString())
                        StatRow("Total solve time", formatSeconds(stats.totalSolveSeconds))
                        StatRow("Average solve time", formatSeconds(stats.averageSeconds))
                        StatRow("Best solve time", stats.bestSeconds?.let { formatSeconds(it) } ?: "—")
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Streaks", style = MaterialTheme.typography.titleMedium)
                        StatRow("Current streak", "${stats.currentStreakDays} day${plural(stats.currentStreakDays)}")
                        StatRow("Longest streak", "${stats.longestStreakDays} day${plural(stats.longestStreakDays)}")
                    }
                }
            }
            if (stats.perSource.isNotEmpty()) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("By source", style = MaterialTheme.typography.titleMedium)
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
                }
            }
        }
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"

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
