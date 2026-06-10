package com.allaway.xwd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.allaway.xwd.data.PuzzleEntity
import com.allaway.xwd.data.formatSeconds
import com.allaway.xwd.ui.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPuzzle: (String) -> Unit,
    onOpenStats: () -> Unit,
) {
    val puzzles by viewModel.puzzles.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PuzzleEntity?>(null) }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbar.showSnackbar(it)
            viewModel.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crosswords") },
                actions = {
                    IconButton(onClick = { showArchiveDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Download from archive")
                    }
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Statistics")
                    }
                    if (viewModel.downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).width(24.dp).height(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { viewModel.downloadLatestFromAll() }) {
                            Icon(Icons.Filled.Download, contentDescription = "Download latest puzzles")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (puzzles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No puzzles yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the download button to fetch today's puzzles from the " +
                        "Wall Street Journal, Universal Crossword, and Jonesin' feeds, " +
                        "or + to pick a date from the archives.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(puzzles, key = { it.id }) { puzzle ->
                    PuzzleCard(
                        puzzle = puzzle,
                        onClick = { onOpenPuzzle(puzzle.id) },
                        onDelete = { pendingDelete = puzzle },
                    )
                }
            }
        }
    }

    if (showArchiveDialog) {
        ArchiveDownloadDialog(
            viewModel = viewModel,
            onDismiss = { showArchiveDialog = false },
        )
    }

    pendingDelete?.let { puzzle ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete puzzle?") },
            text = { Text("“${puzzle.title}” and its progress will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(puzzle.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PuzzleCard(puzzle: PuzzleEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    puzzle.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${puzzle.sourceName} · ${puzzle.date}" +
                        (if (puzzle.author.isNotBlank()) " · ${puzzle.author}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                if (puzzle.isCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(18.dp).height(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Solved in ${formatSeconds(puzzle.elapsedSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    val fraction =
                        if (puzzle.whiteCount == 0) 0f
                        else puzzle.filledCount.toFloat() / puzzle.whiteCount
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${(fraction * 100).toInt()}% · ${formatSeconds(puzzle.elapsedSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveDownloadDialog(viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    var selectedSource by remember { mutableStateOf(viewModel.sources.first()) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    dateState.selectedDateMillis?.let {
                        viewModel.downloadFor(selectedSource, it)
                    }
                    onDismiss()
                },
                enabled = dateState.selectedDateMillis != null,
            ) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            viewModel.sources.forEach { source ->
                FilterChip(
                    selected = source == selectedSource,
                    onClick = { selectedSource = source },
                    label = { Text(source.name, maxLines = 1) },
                )
            }
        }
        DatePicker(state = dateState, showModeToggle = false)
    }
}
