package app.xwd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xwd.data.SizeHeatmaps
import app.xwd.data.Stats
import app.xwd.data.formatSeconds
import app.xwd.model.SizeClass
import app.xwd.ui.StatsViewModel
import app.xwd.ui.theme.DottedRule
import app.xwd.ui.theme.LocalSkin
import app.xwd.ui.theme.MarginsT
import app.xwd.ui.theme.RisoT
import app.xwd.ui.theme.Skin
import app.xwd.ui.theme.TermT
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val stats = viewModel.stats

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (stats == null) return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (LocalSkin.current) {
                Skin.MARGINS -> MarginsStats(stats, onBack)
                Skin.TERMINAL -> TermStats(stats, onBack)
                Skin.OVERPRINT -> RisoStats(stats, onBack)
            }
        }
    }
}

@Composable
private fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

private fun cellSizeFor(resolution: Int): Dp = when {
    resolution <= 3 -> 30.dp
    resolution <= 5 -> 22.dp
    resolution <= 7 -> 17.dp
    resolution <= 9 -> 14.dp
    else -> 12.dp
}

/** One start/finish heatmap pair, colored by the calling skin. */
@Composable
private fun HeatPair(
    maps: SizeHeatmaps,
    labelColor: Color,
    cellColor: (Float) -> Color,
    cellRadius: Dp,
    startLabel: String = "first square",
    finishLabel: String = "last square",
) {
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        maps.start?.let { HeatGrid(it.weights, maps.resolution, startLabel, labelColor, cellColor, cellRadius) }
        maps.finish?.let { HeatGrid(it.weights, maps.resolution, finishLabel, labelColor, cellColor, cellRadius) }
    }
}

@Composable
private fun HeatGrid(
    weights: List<Float>,
    resolution: Int,
    label: String,
    labelColor: Color,
    cellColor: (Float) -> Color,
    cellRadius: Dp,
) {
    val cell = cellSizeFor(resolution)
    Column {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(resolution) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(resolution) { c ->
                        Box(
                            Modifier
                                .size(cell)
                                .background(cellColor(weights[r * resolution + c]), RoundedCornerShape(cellRadius)),
                        )
                    }
                }
            }
        }
        Text(label, fontSize = 11.sp, color = labelColor, modifier = Modifier.padding(top = 5.dp))
    }
}

/* ===================== Skin 01 · Margins — "Notebook" ===================== */

@Composable
private fun MarginsStats(stats: Stats, onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MarginsT.bg),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("←", fontSize = 17.sp, color = MarginsT.graphite, modifier = Modifier.tap(onBack))
                Text(
                    "THE SOLVER’S",
                    fontSize = 12.sp,
                    letterSpacing = 2.2.sp,
                    color = MarginsT.muted,
                )
            }
            Text(
                "Notebook",
                fontSize = 26.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                color = MarginsT.ink,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                MarginsBigNum(stats.solvedCount.toString(), "puzzles solved")
                MarginsBigNum(stats.cleanSolves.toString(), "clean solves")
                MarginsBigNum(stats.bestSeconds?.let { formatSeconds(it) } ?: "–", "best time")
            }
            MarginsRow("Total time in the grid", formatSeconds(stats.totalSolveSeconds))
            MarginsRow("Average solve", formatSeconds(stats.averageSeconds))
            stats.secondsPerSquare?.let {
                MarginsRow("Seconds per square", String.format(Locale.US, "%.1fs", it))
            }
            if (stats.averageGridWidth != null && stats.averageGridHeight != null) {
                MarginsRow("Average grid", "${stats.averageGridWidth}×${stats.averageGridHeight}")
            }
            if (stats.currentStreak > 0 || stats.longestStreak > 0) {
                MarginsRow(
                    "Current streak",
                    if (stats.currentStreak > 0) "${stats.currentStreak} ${if (stats.currentStreak == 1) "day" else "days"}" else "–",
                )
                MarginsRow(
                    "Longest streak",
                    "${stats.longestStreak} ${if (stats.longestStreak == 1) "day" else "days"}",
                )
            }
            if (stats.bestBySize.isNotEmpty()) {
                MarginsH3("Personal bests")
                SizeClass.entries.filter { it in stats.bestBySize }.forEach { size ->
                    MarginsRow(size.label, formatSeconds(stats.bestBySize.getValue(size)))
                }
            }
        }
        if (stats.heatmapsBySize.isNotEmpty()) {
            item { MarginsH3("Where you start · where you finish") }
            stats.heatmapsBySize.forEach { maps ->
                item {
                    Text(
                        "${maps.sizeClass.label} — ${maps.samples} " + if (maps.samples == 1) "solve" else "solves",
                        fontSize = 12.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = MarginsT.muted,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                    HeatPair(
                        maps,
                        labelColor = MarginsT.muted,
                        cellColor = { MarginsT.graphite.copy(alpha = 0.08f + 0.84f * it) },
                        cellRadius = 2.dp,
                    )
                }
            }
        }
        if (stats.solvesByDayOfWeek.any { it > 0 }) {
            item {
                MarginsH3("Solving rhythm")
                WeekBars(
                    stats.solvesByDayOfWeek,
                    barColor = { MarginsT.graphite.copy(alpha = 0.85f) },
                    labelColor = MarginsT.muted,
                    italicLabels = true,
                )
            }
        }
        if (stats.perSource.isNotEmpty()) {
            item { MarginsH3("By source") }
            stats.perSource.forEach { s ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.5.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(s.sourceName, fontSize = 13.5.sp, color = MarginsT.ink)
                        DottedRule(
                            MarginsT.dotted,
                            Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        Text(
                            "${s.solved} solved, avg ${formatSeconds(s.averageSeconds)}",
                            fontSize = 13.5.sp,
                            fontStyle = FontStyle.Italic,
                            color = MarginsT.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarginsBigNum(value: String, label: String) {
    Column {
        Text(value, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = MarginsT.ink, lineHeight = 42.sp)
        Text(label, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = MarginsT.muted)
    }
}

@Composable
private fun MarginsRow(label: String, value: String) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(label, fontSize = 14.sp, color = MarginsT.ink)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MarginsT.ink)
        }
        DottedRule(MarginsT.dotted, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MarginsH3(text: String) {
    Text(
        text.uppercase(Locale.US),
        fontSize = 11.sp,
        letterSpacing = 2.2.sp,
        color = MarginsT.muted,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/* ===================== Skin 02 · Terminal — "xwd --stats" ===================== */

@Composable
private fun TermStats(stats: Stats, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(TermT.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("←", color = TermT.dim, fontSize = 13.sp, modifier = Modifier.tap(onBack))
            Text("xwd --stats", color = TermT.green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Box(Modifier.weight(1f).height(1.dp).background(TermT.border))
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    TermBigNum(stats.solvedCount.toString(), "solved")
                    TermBigNum(stats.cleanSolves.toString(), "clean")
                    TermBigNum(stats.bestSeconds?.let { formatSeconds(it) } ?: "--", "best")
                }
                TermSect("time")
                TermKv("total_in_grid", formatSeconds(stats.totalSolveSeconds))
                TermKv("avg_solve", formatSeconds(stats.averageSeconds))
                stats.secondsPerSquare?.let {
                    TermKv("sec_per_square", String.format(Locale.US, "%.1fs", it))
                }
                if (stats.averageGridWidth != null && stats.averageGridHeight != null) {
                    TermKv("avg_grid", "${stats.averageGridWidth}x${stats.averageGridHeight}")
                }
                TermSect("streak")
                TermKv("current", if (stats.currentStreak > 0) "${stats.currentStreak}d" else "--")
                TermKv("longest", if (stats.longestStreak > 0) "${stats.longestStreak}d" else "--")
                if (stats.bestBySize.isNotEmpty()) {
                    TermSect("personal bests")
                    SizeClass.entries.filter { it in stats.bestBySize }.forEach { size ->
                        TermKv(size.label.lowercase(Locale.US), formatSeconds(stats.bestBySize.getValue(size)))
                    }
                }
            }
            if (stats.heatmapsBySize.isNotEmpty()) {
                item { TermSect("grid heat — start / finish") }
                stats.heatmapsBySize.forEach { maps ->
                    item {
                        Text(
                            "${maps.sizeClass.label.lowercase(Locale.US)} (${maps.resolution}x${maps.resolution}) · ${maps.samples} solves",
                            color = TermT.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                        )
                        HeatPair(
                            maps,
                            labelColor = TermT.dim,
                            cellColor = { TermT.green.copy(alpha = 0.06f + 0.8f * it) },
                            cellRadius = 2.dp,
                        )
                    }
                }
            }
            if (stats.solvesByDayOfWeek.any { it > 0 }) {
                item {
                    TermSect("rhythm by weekday")
                    val max = stats.solvesByDayOfWeek.max().coerceAtLeast(1)
                    val blocks = "▁▂▃▄▅▆▇█"
                    val spark = stats.solvesByDayOfWeek.joinToString(" ") { v ->
                        if (v == 0) " " else blocks[((v * 8 / max) - 1).coerceIn(0, 7)].toString()
                    }
                    Text(spark, color = TermT.green, fontSize = 13.sp, letterSpacing = 1.sp)
                    Text("M T W T F S S", color = TermT.mid, fontSize = 13.sp, letterSpacing = 1.sp)
                }
            }
            if (stats.perSource.isNotEmpty()) {
                item { TermSect("by source") }
                stats.perSource.forEach { s ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                s.sourceName.lowercase(Locale.US).replace("’", ""),
                                color = TermT.mid,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${s.solved}", color = TermT.bright, fontSize = 12.sp)
                            Text(
                                "  avg ${formatSeconds(s.averageSeconds)}",
                                color = TermT.bright,
                                fontSize = 12.sp,
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(TermT.rowDivider))
                    }
                }
            }
        }
        val since = stats.firstSolveEpochMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }
        Row(
            Modifier.fillMaxWidth().background(TermT.modeBg).padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row {
                Text("stats", color = TermT.green, fontSize = 11.sp)
                if (since != null) Text(" · since $since", color = TermT.mid, fontSize = 11.sp)
            }
            Text("q:back", color = TermT.mid, fontSize = 11.sp, modifier = Modifier.tap(onBack))
        }
    }
}

@Composable
private fun TermBigNum(value: String, label: String) {
    Column {
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TermT.green, lineHeight = 37.sp)
        Text(label, fontSize = 11.sp, color = TermT.dim)
    }
}

@Composable
private fun TermSect(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = TermT.dim, fontSize = 11.sp)
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF1E281E)))
    }
}

@Composable
private fun TermKv(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = TermT.mid, fontSize = 12.5.sp)
        Text(value, color = TermT.bright, fontSize = 12.5.sp)
    }
}

/* ===================== Skin 03 · Overprint — "Your numbers" ===================== */

@Composable
private fun RisoStats(stats: Stats, onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(RisoT.bg),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("←", fontSize = 18.sp, color = RisoT.blue, modifier = Modifier.tap(onBack))
                Text(
                    "THE PRINT RUN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = RisoT.pinkDeep,
                )
            }
            Text(
                "Your numbers",
                style = TextStyle(
                    shadow = Shadow(color = RisoT.pink.copy(alpha = 0.8f), offset = Offset(5f, 4f)),
                ),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                color = RisoT.blue,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
                RisoBigNum(stats.solvedCount.toString(), "solved", RisoT.blue)
                RisoBigNum(stats.cleanSolves.toString(), "clean", Color(0xFFE62C9B))
                RisoBigNum(stats.bestSeconds?.let { formatSeconds(it) } ?: "–", "best time", RisoT.blue)
            }
            RisoKv("total in the grid", formatSeconds(stats.totalSolveSeconds))
            RisoKv("average solve", formatSeconds(stats.averageSeconds))
            stats.secondsPerSquare?.let {
                RisoKv("seconds per square", String.format(Locale.US, "%.1fs", it))
            }
            if (stats.averageGridWidth != null && stats.averageGridHeight != null) {
                RisoKv("average grid", "${stats.averageGridWidth}×${stats.averageGridHeight}")
            }
            if (stats.currentStreak > 0 || stats.longestStreak > 0) {
                RisoKv(
                    "current streak",
                    if (stats.currentStreak > 0) "${stats.currentStreak} days" else "–",
                )
                RisoKv("longest streak", "${stats.longestStreak} days")
            }
            if (stats.bestBySize.isNotEmpty()) {
                RisoH3("personal bests")
                SizeClass.entries.filter { it in stats.bestBySize }.forEach { size ->
                    RisoKv(size.label.lowercase(Locale.US), formatSeconds(stats.bestBySize.getValue(size)))
                }
            }
        }
        if (stats.heatmapsBySize.isNotEmpty()) {
            item { RisoH3("start · finish") }
            stats.heatmapsBySize.forEach { maps ->
                item {
                    Text(
                        "${maps.sizeClass.label.lowercase(Locale.US)} · ${maps.samples} " +
                            if (maps.samples == 1) "solve" else "solves",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RisoT.blueMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 7.dp),
                    )
                    HeatPair(
                        maps,
                        labelColor = RisoT.blueMuted,
                        cellColor = { t ->
                            when {
                                t > 0.66f -> Color(0xFFE62C9B)
                                t > 0.33f -> RisoT.purple
                                else -> RisoT.blue.copy(alpha = 0.15f + t)
                            }
                        },
                        cellRadius = 4.dp,
                    )
                }
            }
        }
        if (stats.solvesByDayOfWeek.any { it > 0 }) {
            item {
                RisoH3("solving rhythm")
                WeekBars(
                    stats.solvesByDayOfWeek,
                    barColor = { RisoT.blue },
                    overprint = RisoT.pink.copy(alpha = 0.65f),
                    labelColor = RisoT.blue,
                    boldLabels = true,
                )
            }
        }
        if (stats.perSource.isNotEmpty()) {
            item { RisoH3("by source") }
            stats.perSource.forEach { s ->
                item {
                    RisoKv(
                        s.sourceName.lowercase(Locale.US),
                        "${s.solved} · avg ${formatSeconds(s.averageSeconds)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun RisoBigNum(value: String, label: String, color: Color) {
    Column {
        Text(value, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = color, lineHeight = 40.sp)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RisoT.blueMuted)
    }
}

@Composable
private fun RisoKv(label: String, value: String) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = RisoT.blue)
            Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = RisoT.blue)
        }
        DottedRule(RisoT.blueFaint, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RisoH3(text: String) {
    Text(
        text.uppercase(Locale.US),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.6.sp,
        color = RisoT.pinkDeep,
        modifier = Modifier.padding(top = 14.dp, bottom = 7.dp),
    )
}

/* ===================== shared ===================== */

@Composable
private fun WeekBars(
    byDay: List<Int>,
    barColor: (Int) -> Color,
    labelColor: Color,
    overprint: Color? = null,
    italicLabels: Boolean = false,
    boldLabels: Boolean = false,
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val max = byDay.max().coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(62.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        byDay.forEach { count ->
            Box(Modifier.weight(1f).height((4 + 54 * count / max).dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (count == 0) labelColor.copy(alpha = 0.15f) else barColor(count),
                            RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                        ),
                )
                // The riso second ink, printed slightly off-register.
                if (overprint != null && count > 0) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 3.dp)
                            .offset(y = (-3).dp)
                            .background(overprint, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        labels.forEach {
            Text(
                it,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                fontStyle = if (italicLabels) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (boldLabels) FontWeight.Bold else FontWeight.Normal,
                color = labelColor,
            )
        }
    }
}

