package app.xwd.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xwd.data.PuzzleEntity
import app.xwd.data.formatSeconds
import app.xwd.model.SizeClass
import app.xwd.sources.PuzzleDownloader.CatalogEntry
import app.xwd.ui.LibraryItem
import app.xwd.ui.LibraryViewModel
import app.xwd.ui.PuzzleType
import app.xwd.ui.theme.DottedRule
import app.xwd.ui.theme.LocalSkin
import app.xwd.ui.theme.MarginsT
import app.xwd.ui.theme.RisoT
import app.xwd.ui.theme.Skin
import app.xwd.ui.theme.TermT
import app.xwd.ui.theme.dashedBorder
import app.xwd.ui.theme.dashedCircleBorder
import app.xwd.ui.theme.offsetShadow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPuzzle: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val puzzles by viewModel.puzzles.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PuzzleEntity?>(null) }

    val feed = viewModel.feed(puzzles, catalog)
    val listState = rememberLazyListState()

    // Re-read feeds/toggles changed in Settings whenever the library resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadConfig()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    val actions = LibraryActions(
        onOpenPuzzle = onOpenPuzzle,
        onArchive = { showArchiveDialog = true },
        onStats = onOpenStats,
        onSettings = onOpenSettings,
        onFilters = { showFilterSheet = true },
        onDelete = { pendingDelete = it },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (LocalSkin.current) {
                Skin.MARGINS -> MarginsLibrary(viewModel, feed, listState, actions)
                Skin.TERMINAL -> TermLibrary(viewModel, feed, catalogSize = catalog.size, listState, actions)
                Skin.OVERPRINT -> RisoLibrary(viewModel, feed, listState, actions)
            }
        }
    }

    if (showArchiveDialog) {
        ArchiveDownloadDialog(viewModel = viewModel, onDismiss = { showArchiveDialog = false })
    }

    if (showFilterSheet) {
        LibraryFilterSheet(viewModel = viewModel, onDismiss = { showFilterSheet = false })
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

private class LibraryActions(
    val onOpenPuzzle: (String) -> Unit,
    val onArchive: () -> Unit,
    val onStats: () -> Unit,
    val onSettings: () -> Unit,
    val onFilters: () -> Unit,
    val onDelete: (PuzzleEntity) -> Unit,
)

private fun LibraryViewModel.sourceName(id: String): String =
    sources.firstOrNull { it.id == id }?.name ?: id

/** One-line description of the active filters for the filter bar. */
private fun filterSummary(viewModel: LibraryViewModel): String {
    val f = viewModel.filters
    val parts = buildList {
        if (f.downloadedOnly) add("downloaded")
        f.size?.let { add(it.label.lowercase(Locale.US)) }
        f.sourceId?.let { add(viewModel.sourceName(it).lowercase(Locale.US)) }
        f.puzzleType?.let { add(if (it == PuzzleType.CRYPTIC) "cryptic" else "normal") }
    }
    return if (parts.isEmpty()) "showing everything" else "showing " + parts.joinToString(" · ")
}

/** Compact, skin-tinted filter bar: opens the filter sheet, shows the summary. */
@Composable
private fun FilterBar(
    label: String,
    summary: String,
    active: Boolean,
    accent: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickableNoRipple(onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text(
            summary,
            color = muted,
            fontSize = 12.5.sp,
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text("clear", color = accent, fontSize = 12.5.sp, modifier = Modifier.clickableNoRipple(onClear))
        }
    }
}

private fun PuzzleEntity.isClean(): Boolean =
    !autocheckUsed && revealCount == 0 && checkCount == 0

private fun progressFraction(p: PuzzleEntity): Float =
    if (p.whiteCount == 0) 0f else p.filledCount.toFloat() / p.whiteCount

private fun todayLine(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))

/* ===================== Skin 01 · Margins ===================== */

@Composable
private fun MarginsLibrary(
    viewModel: LibraryViewModel,
    feed: List<LibraryItem>,
    listState: LazyListState,
    actions: LibraryActions,
) {
    Column(Modifier.fillMaxSize().background(MarginsT.bg)) {
        // Masthead: spaced-caps date, italic wordmark, dotted text actions.
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)) {
            Text(
                todayLine().uppercase(Locale.US),
                fontSize = 11.sp,
                letterSpacing = 2.4.sp,
                color = MarginsT.muted,
            )
            Row {
                Text(
                    "xwd",
                    fontSize = 44.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                    color = MarginsT.ink,
                )
                Text(
                    ".",
                    fontSize = 44.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    color = MarginsT.ochre,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                MarginsAction("archive", actions.onArchive)
                MarginsAction("notebook", actions.onStats)
                MarginsAction("settings", actions.onSettings)
            }
        }
        FilterBar(
            label = "filter",
            summary = filterSummary(viewModel),
            active = viewModel.filters.isActive,
            accent = MarginsT.ochreDeep,
            muted = MarginsT.muted,
            modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
            onOpen = actions.onFilters,
            onClear = viewModel::clearFilters,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(MarginsT.divider))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(feed, key = { it.id }) { item ->
                when (item) {
                    is LibraryItem.Saved -> MarginsSavedCard(item.entity, actions)
                    is LibraryItem.Remote -> MarginsRemoteCard(
                        item.entry,
                        sourceName = viewModel.sourceName(item.entry.sourceId),
                        downloading = item.id in viewModel.downloadingIds,
                        onDownload = { viewModel.download(item.entry) },
                    )
                }
            }
            if (viewModel.loadingMore) {
                items(3) { MarginsSkeleton() }
            }
            emptyAndEndNotes(
                feed, viewModel,
                color = MarginsT.muted,
                endText = "You’ve reached the beginning of the archives.",
            )
        }
    }
}

@Composable
private fun MarginsAction(label: String, onClick: () -> Unit) {
    // IntrinsicSize.Min keeps the dotted underline exactly as wide as the
    // label. A bare fillMaxWidth here would swallow the whole actions row,
    // squeezing the remaining actions to zero width and off the screen.
    Column(
        Modifier
            .width(IntrinsicSize.Min)
            .clickableNoRipple(onClick)
            .padding(vertical = 2.dp),
    ) {
        Text(label, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MarginsT.muted, maxLines = 1)
        DottedRule(MarginsT.faint, Modifier.fillMaxWidth().padding(top = 1.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarginsCardFrame(
    ghost: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (ghost) Color.Transparent else MarginsT.card, shape)
            .let {
                if (ghost) it.dashedBorder(MarginsT.cardBorder, shape)
                else it.border(1.dp, MarginsT.cardBorder, shape)
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) { content() }
}

@Composable
private fun MarginsSavedCard(p: PuzzleEntity, actions: LibraryActions) {
    MarginsCardFrame(
        ghost = false,
        onClick = { actions.onOpenPuzzle(p.id) },
        onLongClick = { actions.onDelete(p) },
    ) {
        Text(
            p.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MarginsT.ink,
            lineHeight = 24.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            SizePillSkinned(SizeClass.forCellCount(p.progress.length).label)
            Spacer(Modifier.width(7.dp))
            Text(
                listOf(p.sourceName, p.date, p.author).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.5.sp,
                fontStyle = FontStyle.Italic,
                color = MarginsT.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (p.isCompleted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("✓", fontSize = 13.sp, color = MarginsT.ochreDeep, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 5.dp))
                Text(
                    "Solved in ${formatSeconds(p.elapsedSeconds)}" + if (p.isClean()) " — clean" else "",
                    fontSize = 13.sp,
                    color = MarginsT.ochreDeep,
                )
            }
        } else {
            val fraction = progressFraction(p)
            val started = fraction > 0f
            if (started) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 13.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        DottedRule(MarginsT.dotted, Modifier.fillMaxWidth())
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(2.5.dp)
                                .background(MarginsT.graphite, RoundedCornerShape(2.dp)),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${(fraction * 100).toInt()}% · ${formatSeconds(p.elapsedSeconds)}",
                        fontSize = 12.sp,
                        color = MarginsT.graphite,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Text(
                    "not started",
                    fontSize = 12.sp,
                    color = MarginsT.faint,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun MarginsRemoteCard(
    entry: CatalogEntry,
    sourceName: String,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    MarginsCardFrame(ghost = true, onClick = onDownload) {
        Text(
            entry.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MarginsT.ink,
            lineHeight = 24.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(sourceName, entry.date?.toString()).joinToString(" · "),
            fontSize = 12.5.sp,
            fontStyle = FontStyle.Italic,
            color = MarginsT.muted,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (downloading) "fetching…" else "tap to fetch ↓",
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            color = MarginsT.muted,
            modifier = Modifier.padding(top = 13.dp),
        )
    }
}

@Composable
private fun SizePillSkinned(label: String) {
    val (border, text) = when (LocalSkin.current) {
        Skin.MARGINS -> MarginsT.pillBorder to MarginsT.graphite
        Skin.TERMINAL -> TermT.keyBorder to TermT.muted
        Skin.OVERPRINT -> RisoT.blueMuted to RisoT.blueBody
    }
    Text(
        label.uppercase(Locale.US),
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        color = text,
        modifier = Modifier
            .border(1.dp, border, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun MarginsSkeleton() {
    MarginsCardFrame(ghost = true, onClick = {}) {
        Box(Modifier.fillMaxWidth(0.55f).height(16.dp).background(MarginsT.divider, RoundedCornerShape(3.dp)))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.35f).height(12.dp).background(MarginsT.divider, RoundedCornerShape(3.dp)))
    }
}

/* ===================== Skin 02 · Terminal ===================== */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TermLibrary(
    viewModel: LibraryViewModel,
    feed: List<LibraryItem>,
    catalogSize: Int,
    listState: LazyListState,
    actions: LibraryActions,
) {
    Column(Modifier.fillMaxSize().background(TermT.bg)) {
        // xwd bar: title, rule, amber [actions].
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("xwd", color = TermT.green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Box(Modifier.weight(1f).height(1.dp).background(TermT.border))
            TermBtn("[☷date]", actions.onArchive)
            TermBtn("[Σstats]", actions.onStats)
            TermBtn("[⚙set]", actions.onSettings)
        }
        FilterBar(
            label = "[filter]",
            summary = filterSummary(viewModel),
            active = viewModel.filters.isActive,
            accent = TermT.amber,
            muted = TermT.muted,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
            onOpen = actions.onFilters,
            onClear = viewModel::clearFilters,
        )
        // The bordered list panel.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
                .border(1.dp, TermT.border, RoundedCornerShape(4.dp)),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(feed, key = { it.id }) { item ->
                when (item) {
                    is LibraryItem.Saved -> TermSavedRow(item.entity, actions)
                    is LibraryItem.Remote -> TermRemoteRow(
                        item.entry,
                        sourceName = viewModel.sourceName(item.entry.sourceId),
                        downloading = item.id in viewModel.downloadingIds,
                        onDownload = { viewModel.download(item.entry) },
                    )
                }
            }
            if (viewModel.loadingMore) {
                item {
                    Text("loading…", color = TermT.dim, fontSize = 11.5.sp, modifier = Modifier.padding(14.dp))
                }
            }
            emptyAndEndNotes(feed, viewModel, color = TermT.dim, endText = "-- end of archives --")
        }
        // Mode line.
        val inProgress = feed.count {
            it is LibraryItem.Saved && !it.entity.isCompleted && progressFraction(it.entity) > 0f
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(TermT.modeBg)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row {
                Text("library", color = TermT.green, fontSize = 11.sp)
                Text(" · ${feed.size} shown · $inProgress in progress", color = TermT.mid, fontSize = 11.sp)
            }
            Text("catalog: %,d".format(catalogSize), color = TermT.mid, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TermBtn(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TermT.amber,
        fontSize = 12.sp,
        modifier = Modifier.clickableNoRipple(onClick).padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TermRow(
    glyph: String,
    glyphColor: Color,
    name: String,
    meta: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondLine: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(glyph, color = glyphColor, fontSize = 12.5.sp, modifier = Modifier.width(20.dp))
            Text(
                name,
                color = TermT.bright,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(meta, color = TermT.dim, fontSize = 11.sp, maxLines = 1)
        }
        Row(
            Modifier.padding(start = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) { secondLine() }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TermT.rowDivider))
}

private fun termGauge(pct: Int): Pair<String, String> {
    val full = (pct / 10).coerceIn(0, 10)
    return "█".repeat(full) to "░".repeat(10 - full)
}

@Composable
private fun TermSavedRow(p: PuzzleEntity, actions: LibraryActions) {
    val meta = listOf(p.sourceName.lowercase(Locale.US), p.date).filter { it.isNotBlank() }.joinToString(" · ")
    if (p.isCompleted) {
        TermRow(
            "✓", TermT.green, p.title.lowercase(Locale.US), meta,
            onClick = { actions.onOpenPuzzle(p.id) },
            onLongClick = { actions.onDelete(p) },
        ) {
            Text("solved ${formatSeconds(p.elapsedSeconds)}", color = TermT.muted, fontSize = 11.5.sp)
            if (p.isClean()) Text("clean", color = TermT.amber, fontSize = 11.5.sp)
        }
    } else {
        val pct = (progressFraction(p) * 100).toInt()
        val started = pct > 0
        TermRow(
            if (started) "▶" else "○",
            if (started) TermT.amber else TermT.dim,
            p.title.lowercase(Locale.US), meta,
            onClick = { actions.onOpenPuzzle(p.id) },
            onLongClick = { actions.onDelete(p) },
        ) {
            if (started) {
                val (full, empty) = termGauge(pct)
                Row {
                    Text(full, color = TermT.green, fontSize = 11.5.sp, letterSpacing = 0.5.sp)
                    Text(empty, color = TermT.keyBorder, fontSize = 11.5.sp, letterSpacing = 0.5.sp)
                }
                Text("$pct%", color = TermT.muted, fontSize = 11.5.sp)
                Text(formatSeconds(p.elapsedSeconds), color = TermT.muted, fontSize = 11.5.sp)
            } else {
                Text("not started", color = TermT.offText, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun TermRemoteRow(
    entry: CatalogEntry,
    sourceName: String,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    val meta = listOfNotNull(sourceName.lowercase(Locale.US), entry.date?.toString()).joinToString(" · ")
    TermRow("↓", TermT.dim, entry.title.lowercase(Locale.US), meta, onClick = onDownload) {
        Text(
            if (downloading) "fetching…" else "tap to fetch · not downloaded",
            color = TermT.muted,
            fontSize = 11.5.sp,
        )
    }
}

/* ===================== Skin 03 · Overprint ===================== */

@Composable
private fun RisoLibrary(
    viewModel: LibraryViewModel,
    feed: List<LibraryItem>,
    listState: LazyListState,
    actions: LibraryActions,
) {
    Column(Modifier.fillMaxSize().background(RisoT.bg)) {
        // Masthead: off-register wordmark + rubber stamps.
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "xwd",
                    style = TextStyle(
                        shadow = Shadow(color = RisoT.pink.copy(alpha = 0.8f), offset = Offset(6f, 5f)),
                    ),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.6).sp,
                    lineHeight = 50.sp,
                    color = RisoT.blue,
                )
                Text(
                    "fresh ink · " + todayLine().substringAfter(", ").lowercase(Locale.US),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    color = RisoT.pinkDeep,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RisoStamp("archive", -6f, actions.onArchive)
                RisoStamp("stats", 3f, actions.onStats)
                RisoStamp("settings", -4f, actions.onSettings)
            }
        }
        FilterBar(
            label = "filter",
            summary = filterSummary(viewModel),
            active = viewModel.filters.isActive,
            accent = RisoT.pinkDeep,
            muted = RisoT.blueMuted,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp),
            onOpen = actions.onFilters,
            onClear = viewModel::clearFilters,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(feed, key = { it.id }) { item ->
                when (item) {
                    is LibraryItem.Saved -> RisoSavedCard(item.entity, actions)
                    is LibraryItem.Remote -> RisoRemoteCard(
                        item.entry,
                        sourceName = viewModel.sourceName(item.entry.sourceId),
                        downloading = item.id in viewModel.downloadingIds,
                        onDownload = { viewModel.download(item.entry) },
                    )
                }
            }
            if (viewModel.loadingMore) {
                items(2) { RisoSkeleton() }
            }
            emptyAndEndNotes(
                feed, viewModel,
                color = RisoT.blueBody,
                endText = "that’s the whole print run.",
            )
        }
    }
}

@Composable
private fun RisoStamp(label: String, rotation: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .rotate(rotation)
            .dashedCircleBorder(RisoT.pinkDeep)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(Locale.US),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = RisoT.pinkDeep,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RisoCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    stamp: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .offsetShadow(RisoT.pinkShadow, shape, dx = 4.dp, dy = 4.dp)
            .background(RisoT.paper, shape)
            .border(2.dp, RisoT.blue, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) { content() }
        if (stamp) {
            Text(
                "SOLVED",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.1.sp,
                color = RisoT.purple,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 14.dp)
                    .rotate(-7f)
                    .border(2.5.dp, RisoT.purple, RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun RisoSavedCard(p: PuzzleEntity, actions: LibraryActions) {
    RisoCard(
        onClick = { actions.onOpenPuzzle(p.id) },
        onLongClick = { actions.onDelete(p) },
        stamp = p.isCompleted,
    ) {
        Text(
            p.title.trim('“', '”', '"'),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            lineHeight = 23.sp,
            color = RisoT.blue,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = if (p.isCompleted) 80.dp else 0.dp),
        )
        RisoMeta(
            p.sourceName,
            listOf(p.date, p.author, SizeClass.forCellCount(p.progress.length).label)
                .filter { it.isNotBlank() }.joinToString(" · "),
        )
        if (p.isCompleted) {
            Text(
                formatSeconds(p.elapsedSeconds) + if (p.isClean()) " — a clean print, no smudges" else "",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = RisoT.purple,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            val fraction = progressFraction(p)
            val started = fraction > 0f
            if (started) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 13.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(12.dp)
                            .border(2.dp, RisoT.blue, RoundedCornerShape(99.dp))
                            .padding(3.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxSize()
                                .background(RisoT.pinkPale, RoundedCornerShape(99.dp)),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${(fraction * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RisoT.blue,
                    )
                }
            } else {
                Text(
                    "↓ not started",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RisoT.blueMuted,
                    modifier = Modifier.padding(top = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun RisoMeta(sourceName: String, rest: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(sourceName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RisoT.pinkDeep, maxLines = 1)
        if (rest.isNotBlank()) {
            Text(
                " · $rest",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = RisoT.blueMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RisoRemoteCard(
    entry: CatalogEntry,
    sourceName: String,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    RisoCard(onClick = onDownload) {
        Text(
            entry.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            lineHeight = 23.sp,
            color = RisoT.blue,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        RisoMeta(sourceName, entry.date?.toString() ?: "")
        Text(
            if (downloading) "↓ pulling a copy…" else "↓ tap to pull a copy",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = RisoT.pinkDeep,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun RisoSkeleton() {
    RisoCard(onClick = {}) {
        Box(Modifier.fillMaxWidth(0.55f).height(18.dp).background(RisoT.blueFaint.copy(alpha = 0.4f), RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.35f).height(12.dp).background(RisoT.blueFaint.copy(alpha = 0.4f), RoundedCornerShape(4.dp)))
    }
}

/* ===================== shared bits ===================== */

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

/** Empty-feed and end-of-archive footnotes, tinted by the calling skin. */
private fun LazyListScope.emptyAndEndNotes(
    feed: List<LibraryItem>,
    viewModel: LibraryViewModel,
    color: Color,
    endText: String,
) {
    if (feed.isEmpty() && !viewModel.loadingMore) {
        item {
            Text(
                when {
                    viewModel.filters.size != null ->
                        "No downloaded puzzles of this size. Size filters only match puzzles already on your device."
                    viewModel.filters.downloadedOnly ->
                        "Nothing downloaded yet. Clear the filter, then tap a puzzle to download it."
                    viewModel.filters.sourceId != null ->
                        "Nothing from this source yet — its puzzles will appear here as they’re found."
                    viewModel.disabledSources.size == viewModel.sources.size ->
                        "All sources are turned off. Turn one back on in Settings."
                    else ->
                        "Puzzles from your enabled sources will appear here as they’re found."
                },
                color = color,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
            )
        }
    }
    if (feed.isNotEmpty() && viewModel.catalogExhausted && !viewModel.loadingMore) {
        item {
            Text(
                endText,
                color = color,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
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

/**
 * One filter surface for all three skins: status, size, and source in a
 * single themed sheet, replacing the long horizontal chip row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilterSheet(viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val filters = viewModel.filters
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            FilterGroup("Show") {
                ChoiceChip("All", selected = !filters.downloadedOnly) { viewModel.setDownloadedOnly(false) }
                ChoiceChip("Downloaded", selected = filters.downloadedOnly) { viewModel.setDownloadedOnly(true) }
            }

            FilterGroup("Size") {
                ChoiceChip("Any size", selected = filters.size == null) { viewModel.setSizeFilter(null) }
                SizeClass.entries.forEach { sc ->
                    ChoiceChip(sc.label, selected = filters.size == sc) { viewModel.setSizeFilter(sc) }
                }
            }
            Text(
                "Size matches puzzles already on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            FilterGroup("Source") {
                ChoiceChip("All sources", selected = filters.sourceId == null) { viewModel.setSourceFilter(null) }
                viewModel.enabledSources.forEach { source ->
                    ChoiceChip(source.name, selected = filters.sourceId == source.id) {
                        viewModel.setSourceFilter(source.id)
                    }
                }
            }

            FilterGroup("Type") {
                ChoiceChip("Any", selected = filters.puzzleType == null) { viewModel.setPuzzleTypeFilter(null) }
                ChoiceChip("Normal", selected = filters.puzzleType == PuzzleType.NORMAL) {
                    viewModel.setPuzzleTypeFilter(PuzzleType.NORMAL)
                }
                ChoiceChip("Cryptic", selected = filters.puzzleType == PuzzleType.CRYPTIC) {
                    viewModel.setPuzzleTypeFilter(PuzzleType.CRYPTIC)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { viewModel.clearFilters() }) { Text("Clear all") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            title.uppercase(Locale.US),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
