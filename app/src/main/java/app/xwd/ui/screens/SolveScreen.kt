package app.xwd.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xwd.data.formatSeconds
import app.xwd.model.Direction
import app.xwd.ui.CompletionState
import app.xwd.ui.SolveViewModel
import app.xwd.ui.grid.CrosswordGrid
import app.xwd.ui.grid.CrosswordKeyboard
import app.xwd.ui.theme.LocalSkin
import app.xwd.ui.theme.MarginsT
import app.xwd.ui.theme.RisoT
import app.xwd.ui.theme.Skin
import app.xwd.ui.theme.TermT
import app.xwd.ui.theme.offsetShadow
import kotlinx.coroutines.delay
import java.util.Locale

/** Minimum lines reserved in the clue bar text to prevent layout shifts. */
private const val ClueBarMinLines = 2

private val XRefPattern = Regex("""(\d+)[\s\-]*(across|down)""", RegexOption.IGNORE_CASE)
private const val XRefTag = "XREF"

/**
 * Annotates cross-references in a clue string (e.g. "32 down") with a link
 * style and a string annotation so taps can jump to the referenced clue.
 */
private fun annotateClue(text: String, linkStyle: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(text)
        XRefPattern.findAll(text).forEach { match ->
            addStyle(linkStyle, match.range.first, match.range.last + 1)
            addStringAnnotation(XRefTag, match.value, match.range.first, match.range.last + 1)
        }
    }

/** Tappable clue text that jumps to referenced clues when the annotation is hit. */
@Composable
private fun ClueText(
    annotated: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    viewModel: SolveViewModel,
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style,
        minLines = ClueBarMinLines,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                layoutResult?.let { lr ->
                    val offset = lr.getOffsetForPosition(pos)
                    annotated.getStringAnnotations(XRefTag, offset, offset).firstOrNull()
                        ?.let { ann ->
                            XRefPattern.find(ann.item)?.let { m ->
                                val num = m.groupValues[1].toIntOrNull() ?: return@let
                                val dir = if (m.groupValues[2].equals("across", ignoreCase = true))
                                    Direction.ACROSS else Direction.DOWN
                                viewModel.selectClueByRef(num, dir)
                            }
                        }
                }
            }
        },
    )
}

/**
 * Smallest readable/tappable cell. Grids that can't fit the screen at this
 * size (supermaxi and up on most phones) render at exactly this size inside
 * a two-axis pan instead of shrinking further.
 */
private val MinSolveCell = 22.dp

/**
 * Scroll one axis just far enough that the cell spanning
 * [edge, edge + cellPx] is visible with a cell of margin.
 */
private suspend fun scrollCellIntoView(
    scroll: ScrollState,
    edge: Float,
    cellPx: Float,
    contentPx: Float,
) {
    if (scroll.maxValue == Int.MAX_VALUE) return // not yet measured
    val viewport = contentPx - scroll.maxValue
    val target = when {
        edge - cellPx < scroll.value -> edge - cellPx
        edge + 2 * cellPx > scroll.value + viewport -> edge + 2 * cellPx - viewport
        else -> return
    }
    scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue))
}

@Composable
fun SolveScreen(viewModel: SolveViewModel, onBack: () -> Unit) {
    val puzzle = viewModel.puzzle
    val skin = LocalSkin.current
    var menuOpen by remember { mutableStateOf(false) }
    var cluesOpen by remember { mutableStateOf(false) }

    // One-second solve timer; pauses automatically once solved.
    LaunchedEffect(viewModel.completionState) {
        while (viewModel.completionState != CompletionState.SOLVED) {
            delay(1000)
            viewModel.tick()
        }
    }

    val back = {
        viewModel.save()
        onBack()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (puzzle == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (skin) {
                Skin.MARGINS -> MarginsSolveTop(
                    viewModel, back,
                    onClues = { cluesOpen = true },
                    menuOpen = menuOpen,
                    onMenu = { menuOpen = it },
                )
                Skin.TERMINAL -> TermSolveTop(
                    viewModel, back,
                    onClues = { cluesOpen = true },
                    menuOpen = menuOpen,
                    onMenu = { menuOpen = it },
                )
                Skin.OVERPRINT -> RisoSolveTop(
                    viewModel, back,
                    onClues = { cluesOpen = true },
                    menuOpen = menuOpen,
                    onMenu = { menuOpen = it },
                )
            }
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
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                val fitCell = minOf(maxWidth / puzzle.width, maxHeight / puzzle.height)
                if (fitCell >= MinSolveCell) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CrosswordGrid(
                            puzzle = puzzle,
                            letters = viewModel.letters,
                            selected = viewModel.selected,
                            currentWord = viewModel.currentWord,
                            isWrong = viewModel::isWrong,
                            revealed = viewModel.revealed,
                            onCellTap = viewModel::selectCell,
                            modifier = Modifier.size(fitCell * puzzle.width, fitCell * puzzle.height),
                            referencedCells = viewModel.referencedCells,
                        )
                    }
                } else {
                    // Grid too large to fit at a readable size: render at a
                    // fixed cell size and pan, keeping the selection in view.
                    val hScroll = rememberScrollState()
                    val vScroll = rememberScrollState()
                    val density = LocalDensity.current
                    LaunchedEffect(viewModel.selected) {
                        val cellPx = with(density) { MinSolveCell.toPx() }
                        scrollCellIntoView(
                            hScroll, puzzle.colOf(viewModel.selected) * cellPx,
                            cellPx, puzzle.width * cellPx,
                        )
                        scrollCellIntoView(
                            vScroll, puzzle.rowOf(viewModel.selected) * cellPx,
                            cellPx, puzzle.height * cellPx,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                            .verticalScroll(vScroll),
                    ) {
                        CrosswordGrid(
                            puzzle = puzzle,
                            letters = viewModel.letters,
                            selected = viewModel.selected,
                            currentWord = viewModel.currentWord,
                            isWrong = viewModel::isWrong,
                            revealed = viewModel.revealed,
                            onCellTap = viewModel::selectCell,
                            modifier = Modifier.size(
                                MinSolveCell * puzzle.width,
                                MinSolveCell * puzzle.height,
                            ),
                            referencedCells = viewModel.referencedCells,
                        )
                    }
                }
            }
            when (skin) {
                Skin.MARGINS -> MarginsClueBar(viewModel)
                Skin.TERMINAL -> TermClueBar(viewModel)
                Skin.OVERPRINT -> RisoClueBar(viewModel)
            }
            CrosswordKeyboard(
                onKey = viewModel::input,
                onBackspace = viewModel::backspace,
            )
            if (skin == Skin.TERMINAL) TermSolveModeLine(viewModel)
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
private fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

private fun fillPercent(viewModel: SolveViewModel): Int {
    val letters = viewModel.letters
    val white = letters.count { it != '.' }
    if (white == 0) return 0
    return 100 * letters.count { it != '.' && it != '-' } / white
}

/* ---------- Margins solve chrome ---------- */

@Composable
private fun MarginsSolveTop(
    viewModel: SolveViewModel,
    onBack: () -> Unit,
    onClues: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
) {
    Column(Modifier.background(MarginsT.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("←", fontSize = 20.sp, color = MarginsT.graphite, modifier = Modifier.tap(onBack).padding(end = 12.dp))
            Text(
                viewModel.puzzle?.title.orEmpty(),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = MarginsT.ink,
                lineHeight = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatSeconds(viewModel.elapsedSeconds) + if (viewModel.timerPaused) " · paused" else "",
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = MarginsT.muted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        ) {
            Text(
                if (viewModel.autocheck) "autocheck ✓" else "autocheck",
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = if (viewModel.autocheck) MarginsT.ochreDeep else MarginsT.muted,
                modifier = Modifier.tap { viewModel.toggleAutocheck() }.padding(vertical = 2.dp),
            )
            Text(
                "clues ☰",
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = MarginsT.muted,
                modifier = Modifier.tap(onClues).padding(vertical = 2.dp),
            )
            Box {
                Text(
                    "more ⋮",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MarginsT.muted,
                    modifier = Modifier.tap { onMenu(true) }.padding(vertical = 2.dp),
                )
                SolveMenu(expanded = menuOpen, onDismiss = { onMenu(false) }, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun MarginsClueBar(viewModel: SolveViewModel) {
    val clue = viewModel.currentClue
    Column(Modifier.background(MarginsT.bg).padding(horizontal = 18.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MarginsT.divider))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹", fontSize = 18.sp, color = MarginsT.faint,
                modifier = Modifier.tap { viewModel.previousClue() }.padding(horizontal = 8.dp),
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                clue?.let {
                    Text(
                        "${it.number}${if (it.direction == Direction.ACROSS) "A" else "D"}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MarginsT.ochreDeep,
                        modifier = Modifier.padding(end = 7.dp),
                    )
                    ClueText(
                        annotated = annotateClue(it.text, SpanStyle(
                            color = MarginsT.ochreDeep,
                            textDecoration = TextDecoration.Underline,
                        )),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontStyle = FontStyle.Italic,
                            color = MarginsT.ink,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        ),
                        viewModel = viewModel,
                    )
                } ?: Text("", fontSize = 15.sp, minLines = ClueBarMinLines)
            }
            Text(
                "›", fontSize = 18.sp, color = MarginsT.faint,
                modifier = Modifier.tap { viewModel.nextClue() }.padding(horizontal = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MarginsT.divider))
    }
}

/* ---------- Terminal solve chrome ---------- */

@Composable
private fun TermSolveTop(
    viewModel: SolveViewModel,
    onBack: () -> Unit,
    onClues: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
) {
    val fileName = viewModel.puzzle?.title.orEmpty()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_') + ".puz"
    Row(
        Modifier
            .fillMaxWidth()
            .background(TermT.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("←", color = TermT.dim, fontSize = 13.sp, modifier = Modifier.tap(onBack))
        Text(
            fileName,
            color = TermT.green,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(Modifier.weight(1f).height(1.dp).background(TermT.border))
        Text(formatSeconds(viewModel.elapsedSeconds), color = TermT.mid, fontSize = 13.sp)
        Text("[≡]", color = TermT.amber, fontSize = 12.sp, modifier = Modifier.tap(onClues))
        Text(
            "[✓?]",
            color = if (viewModel.autocheck) TermT.green else TermT.amber,
            fontSize = 12.sp,
            modifier = Modifier.tap { viewModel.toggleAutocheck() },
        )
        Box {
            Text("[⋮]", color = TermT.amber, fontSize = 12.sp, modifier = Modifier.tap { onMenu(true) })
            SolveMenu(expanded = menuOpen, onDismiss = { onMenu(false) }, viewModel = viewModel)
        }
    }
}

@Composable
private fun TermClueBar(viewModel: SolveViewModel) {
    val clue = viewModel.currentClue
    Row(
        Modifier
            .fillMaxWidth()
            .background(TermT.bg)
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .border(1.dp, TermT.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("‹", color = TermT.dim, fontSize = 13.sp, modifier = Modifier.tap { viewModel.previousClue() })
        clue?.let {
            Text(
                "${it.number}${if (it.direction == Direction.ACROSS) "A" else "D"}",
                color = TermT.amber,
                fontSize = 13.sp,
            )
            ClueText(
                annotated = annotateClue(it.text.lowercase(Locale.US), SpanStyle(
                    color = TermT.amber,
                    textDecoration = TextDecoration.Underline,
                )),
                style = TextStyle(
                    color = TermT.bright,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
            )
        } ?: Text("", fontSize = 13.sp, minLines = ClueBarMinLines, modifier = Modifier.weight(1f))
        Text("›", color = TermT.dim, fontSize = 13.sp, modifier = Modifier.tap { viewModel.nextClue() })
    }
}

@Composable
private fun TermSolveModeLine(viewModel: SolveViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TermT.modeBg)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Text("-- ", color = TermT.mid, fontSize = 11.sp)
            Text(
                if (viewModel.direction == Direction.ACROSS) "ACROSS" else "DOWN",
                color = TermT.green,
                fontSize = 11.sp,
            )
            Text(" --", color = TermT.mid, fontSize = 11.sp)
        }
        Row {
            Text("autocheck:", color = TermT.mid, fontSize = 11.sp)
            Text(if (viewModel.autocheck) "on" else "off", color = TermT.green, fontSize = 11.sp)
            Text(" · ${fillPercent(viewModel)}% filled", color = TermT.mid, fontSize = 11.sp)
        }
    }
}

/* ---------- Overprint solve chrome ---------- */

@Composable
private fun RisoSolveTop(
    viewModel: SolveViewModel,
    onBack: () -> Unit,
    onClues: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
) {
    Column(Modifier.background(RisoT.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("←", fontSize = 20.sp, color = RisoT.blue, modifier = Modifier.tap(onBack).padding(end = 12.dp))
            Text(
                viewModel.puzzle?.title.orEmpty().trim('“', '”', '"'),
                style = TextStyle(
                    shadow = Shadow(color = RisoT.pink.copy(alpha = 0.8f), offset = Offset(4f, 3f)),
                ),
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                lineHeight = 22.sp,
                color = RisoT.blue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatSeconds(viewModel.elapsedSeconds),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = RisoT.pinkDeep,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .border(2.dp, RisoT.pink, RoundedCornerShape(99.dp))
                    .padding(horizontal = 11.dp, vertical = 3.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            RisoTool("☰ clues", on = false, onClick = onClues)
            RisoTool("✓ autocheck", on = viewModel.autocheck) { viewModel.toggleAutocheck() }
            Box {
                RisoTool("⋮", on = false) { onMenu(true) }
                SolveMenu(expanded = menuOpen, onDismiss = { onMenu(false) }, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun RisoTool(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(99.dp)
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (on) RisoT.paper else RisoT.blue,
        modifier = Modifier
            .background(if (on) RisoT.pink else Color.Transparent, shape)
            .border(2.dp, if (on) RisoT.pink else RisoT.blue, shape)
            .tap(onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun RisoClueBar(viewModel: SolveViewModel) {
    val clue = viewModel.currentClue
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.background(RisoT.bg).padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .offsetShadow(RisoT.pinkShadow, shape, dx = 3.dp, dy = 3.dp)
                .background(RisoT.paper, shape)
                .border(2.dp, RisoT.blue, shape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("‹", color = RisoT.blueMuted, fontSize = 17.sp, modifier = Modifier.tap { viewModel.previousClue() })
            clue?.let {
                Text(
                    "${it.number}${if (it.direction == Direction.ACROSS) "A" else "D"}",
                    color = RisoT.pinkDeep,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
                ClueText(
                    annotated = annotateClue(it.text, SpanStyle(
                        color = RisoT.pinkDeep,
                        textDecoration = TextDecoration.Underline,
                    )),
                    style = TextStyle(
                        color = RisoT.blue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp,
                    ),
                    modifier = Modifier.weight(1f),
                    viewModel = viewModel,
                )
            } ?: Text("", fontSize = 14.sp, minLines = ClueBarMinLines, modifier = Modifier.weight(1f))
            Text("›", color = RisoT.blueMuted, fontSize = 17.sp, modifier = Modifier.tap { viewModel.nextClue() })
        }
    }
}

/* ---------- shared sheets and dialogs ---------- */

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
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(clues, key = { "${it.direction}-${it.number}" }) { clue ->
                val filled = clue.cells.all { viewModel.letters[it] != '-' }
                val correct = filled && clue.cells.none { viewModel.isWrong(it) } &&
                    clue.cells.all { viewModel.letters[it] != '-' }
                val isCurrent = clue == viewModel.currentClue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectClue(clue)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                            correct -> MaterialTheme.colorScheme.onSurfaceVariant
                            filled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (correct) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else if (filled) {
                        Text(
                            "…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
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
