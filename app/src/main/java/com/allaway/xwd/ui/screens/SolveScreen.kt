package com.allaway.xwd.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allaway.xwd.data.formatSeconds
import com.allaway.xwd.model.Clue
import com.allaway.xwd.model.Direction
import com.allaway.xwd.ui.CompletionState
import com.allaway.xwd.ui.SolveViewModel
import com.allaway.xwd.ui.grid.CrosswordGrid
import com.allaway.xwd.ui.grid.CrosswordKeyboard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolveScreen(viewModel: SolveViewModel, onBack: () -> Unit) {
    val puzzle = viewModel.puzzle
    var menuOpen by remember { mutableStateOf(false) }
    var cluesOpen by remember { mutableStateOf(false) }

    // One-second solve timer; pauses automatically once solved.
    LaunchedEffect(viewModel.completionState) {
        while (viewModel.completionState != CompletionState.SOLVED) {
            delay(1000)
            viewModel.tick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            puzzle?.title ?: "Loading…",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            formatSeconds(viewModel.elapsedSeconds) +
                                if (viewModel.autocheck) "  ·  Autocheck on" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.save()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { cluesOpen = true }) {
                        Icon(Icons.Outlined.List, contentDescription = "All clues")
                    }
                    IconButton(onClick = { viewModel.toggleAutocheck() }) {
                        Icon(
                            Icons.Outlined.FactCheck,
                            contentDescription = "Toggle autocheck",
                            tint = if (viewModel.autocheck) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        SolveMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            viewModel = viewModel,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (puzzle == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.isPhotoImport) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        "Imported from a photo — checks compare against an AI-reconstructed " +
                            "solution, which may contain errors.",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                CrosswordGrid(
                    puzzle = puzzle,
                    letters = viewModel.letters,
                    selected = viewModel.selected,
                    currentWord = viewModel.currentWord,
                    isWrong = viewModel::isWrong,
                    revealed = viewModel.revealed,
                    onCellTap = viewModel::selectCell,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(puzzle.width.toFloat() / puzzle.height),
                )
            }
            ClueBar(viewModel)
            CrosswordKeyboard(
                onKey = viewModel::input,
                onBackspace = viewModel::backspace,
            )
        }
    }

    if (cluesOpen && puzzle != null) {
        ClueSheet(
            viewModel = viewModel,
            onDismiss = { cluesOpen = false },
        )
    }

    if (viewModel.showCompletionDialog) {
        CompletionDialog(viewModel)
    }
}

@Composable
private fun SolveMenu(expanded: Boolean, onDismiss: () -> Unit, viewModel: SolveViewModel) {
    val locked = viewModel.puzzle?.scrambled == true
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Check letter") }, enabled = !locked,
            onClick = { viewModel.checkLetter(); onDismiss() })
        DropdownMenuItem(text = { Text("Check word") }, enabled = !locked,
            onClick = { viewModel.checkWord(); onDismiss() })
        DropdownMenuItem(text = { Text("Check puzzle") }, enabled = !locked,
            onClick = { viewModel.checkPuzzle(); onDismiss() })
        HorizontalDivider()
        DropdownMenuItem(text = { Text("Reveal letter") }, enabled = !locked,
            onClick = { viewModel.revealLetter(); onDismiss() })
        DropdownMenuItem(text = { Text("Reveal word") }, enabled = !locked,
            onClick = { viewModel.revealWord(); onDismiss() })
        DropdownMenuItem(text = { Text("Reveal puzzle") }, enabled = !locked,
            onClick = { viewModel.revealPuzzle(); onDismiss() })
        HorizontalDivider()
        DropdownMenuItem(text = { Text("Clear puzzle") },
            onClick = { viewModel.clearPuzzle(); onDismiss() })
    }
}

@Composable
private fun ClueBar(viewModel: SolveViewModel) {
    val clue = viewModel.currentClue
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.previousClue() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous clue")
            }
            Text(
                text = clue?.let {
                    "${it.number}${if (it.direction == Direction.ACROSS) "A" else "D"}  ${it.text}"
                } ?: "",
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { viewModel.nextClue() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next clue")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClueSheet(viewModel: SolveViewModel, onDismiss: () -> Unit) {
    val puzzle = viewModel.puzzle ?: return
    var tab by remember { mutableStateOf(viewModel.direction) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Direction.entries.forEachIndexed { i, dir ->
                SegmentedButton(
                    selected = tab == dir,
                    onClick = { tab = dir },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                ) {
                    Text(if (dir == Direction.ACROSS) "Across" else "Down")
                }
            }
        }
        val clues = puzzle.cluesFor(tab)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            items(clues, key = { "${it.direction}-${it.number}" }) { clue ->
                val filled = clue.cells.all { viewModel.letters[it] != '-' }
                val isCurrent = clue == viewModel.currentClue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectClue(clue)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        clue.number.toString(),
                        modifier = Modifier.padding(end = 12.dp),
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        clue.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            filled -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionDialog(viewModel: SolveViewModel) {
    val solved = viewModel.completionState == CompletionState.SOLVED
    AlertDialog(
        onDismissRequest = { viewModel.dismissCompletionDialog() },
        title = { Text(if (solved) "Congratulations! 🎉" else "Almost there…") },
        text = {
            Text(
                if (solved) {
                    "You solved “${viewModel.puzzle?.title}” in " +
                        formatSeconds(viewModel.elapsedSeconds) + "."
                } else {
                    "The grid is full, but at least one square isn't right. " +
                        "Try Autocheck or Check puzzle to find the errors."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.dismissCompletionDialog() }) {
                Text(if (solved) "Nice!" else "Keep going")
            }
        },
    )
}
