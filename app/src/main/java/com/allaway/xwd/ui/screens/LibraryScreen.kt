package com.allaway.xwd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.allaway.xwd.data.PuzzleEntity
import com.allaway.xwd.data.formatSeconds
import com.allaway.xwd.model.SizeClass
import com.allaway.xwd.sources.PuzzleDownloader.CatalogEntry
import com.allaway.xwd.ui.ImportViewModel
import com.allaway.xwd.ui.LibraryItem
import com.allaway.xwd.ui.LibraryViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

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
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PuzzleEntity?>(null) }
    val importViewModel: ImportViewModel = viewModel()

    val feed = viewModel.feed(puzzles)
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbar.showSnackbar(it)
            viewModel.message = null
        }
    }

    // Load the next slice of the catalog whenever the user nears the end.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            (last >= info.totalItemsCount - 6) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (nearEnd, _) -> if (nearEnd) viewModel.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "xwd",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = "Import from photo")
                    }
                    IconButton(onClick = { showArchiveDialog = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Download a specific date")
                    }
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Statistics")
                    }
                    if (viewModel.downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(24.dp),
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            SourceFilterRow(viewModel)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(feed, key = { it.id }) { item ->
                    when (item) {
                        is LibraryItem.Saved -> SavedPuzzleCard(
                            puzzle = item.entity,
                            onClick = { onOpenPuzzle(item.entity.id) },
                            onDelete = { pendingDelete = item.entity },
                        )
                        is LibraryItem.Remote -> RemotePuzzleCard(
                            entry = item.entry,
                            sourceName = viewModel.sources
                                .firstOrNull { it.id == item.entry.sourceId }?.name
                                ?: item.entry.sourceId,
                            downloading = item.id in viewModel.downloadingIds,
                            onDownload = { viewModel.download(item.entry) },
                        )
                    }
                }
                if (viewModel.loadingMore) {
                    items(3) { SkeletonCard() }
                }
                if (feed.isEmpty() && !viewModel.loadingMore) {
                    item { EmptyLibrary(allSourcesOff = viewModel.disabledSources.size == viewModel.sources.size) }
                }
                if (feed.isNotEmpty() && viewModel.catalogExhausted && !viewModel.loadingMore) {
                    item {
                        Text(
                            "You've reached the beginning of the archives.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                    }
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

    if (showImportDialog) {
        ImportDialog(
            viewModel = importViewModel,
            onDismiss = { showImportDialog = false },
            onOpenPuzzle = onOpenPuzzle,
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
private fun SourceFilterRow(viewModel: LibraryViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(viewModel.sources, key = { it.id }) { source ->
            val enabled = source.id !in viewModel.disabledSources
            FilterChip(
                selected = enabled,
                onClick = { viewModel.toggleSource(source.id) },
                label = { Text(source.name, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun SavedPuzzleCard(puzzle: PuzzleEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    puzzle.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SizePill(SizeClass.forCellCount(puzzle.progress.length).label)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOf(puzzle.sourceName, puzzle.date, puzzle.author)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (puzzle.isCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Solved in ${formatSeconds(puzzle.elapsedSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            if (fraction == 0f) "Not started"
                            else "${(fraction * 100).toInt()}% · ${formatSeconds(puzzle.elapsedSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RemotePuzzleCard(
    entry: CatalogEntry,
    sourceName: String,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    OutlinedCard(onClick = onDownload, enabled = !downloading) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(sourceName, entry.date?.toString())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(12.dp).size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = "Download ${entry.title}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Tiny tonal badge naming the grid's size class (Mini .. Ultramaxi). */
@Composable
private fun SizePill(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Placeholder card matching the feed card shape while the catalog loads. */
@Composable
private fun SkeletonCard() {
    val tone = MaterialTheme.colorScheme.surfaceVariant
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                Modifier.fillMaxWidth(0.55f).height(16.dp)
                    .background(tone, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth(0.35f).height(12.dp)
                    .background(tone, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(allSourcesOff: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No puzzles here", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (allSourcesOff) {
                "All sources are turned off. Turn one back on above to browse its puzzles."
            } else {
                "Puzzles from your enabled sources will appear here as they're " +
                    "found. Tap a card to download one and start solving."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveDownloadDialog(viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    var selectedSource by remember { mutableStateOf(viewModel.datedSources.first()) }
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
            viewModel.datedSources.forEach { source ->
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
