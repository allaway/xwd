package app.xwd.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.xwd.data.Backup
import app.xwd.data.FailureReason
import app.xwd.sources.PuzzleSources
import app.xwd.ui.BulkState
import app.xwd.ui.SettingsViewModel
import app.xwd.ui.TransferState
import app.xwd.ui.theme.Skin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    currentSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    onBack: () -> Unit,
) {
    var showAddFeed by remember { mutableStateOf(false) }

    // A restored backup can carry a different skin; apply it as soon as it lands.
    val restoredSkin = viewModel.restoredSkinName
    LaunchedEffect(restoredSkin) {
        if (restoredSkin != null) {
            Skin.entries.firstOrNull { it.name == restoredSkin }
                ?.takeIf { it != currentSkin }
                ?.let(onSkinChange)
            viewModel.consumeRestoredSkin()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { AppearanceSection(currentSkin, onSkinChange) }
            item { SolvingSection(viewModel) }
            item { FeedsSection(viewModel, onAddFeed = { showAddFeed = true }) }
            item { DownloadsSection(viewModel) }
            item { TransferSection(viewModel) }
        }
    }

    if (showAddFeed) {
        AddFeedDialog(
            onDismiss = { showAddFeed = false },
            onAdd = { name, url -> viewModel.addCustomFeed(name, url) },
        )
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(currentSkin: Skin, onSkinChange: (Skin) -> Unit) {
    SectionCard("Appearance", "Pick the look and feel. Changes apply instantly.") {
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Skin.entries.forEach { skin ->
                FilterChip(
                    selected = skin == currentSkin,
                    onClick = { onSkinChange(skin) },
                    label = { Text(skin.label) },
                )
            }
        }
        Text(
            currentSkin.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SolvingSection(viewModel: SettingsViewModel) {
    SectionCard("Solving") {
        SettingRow(
            title = "Real-time checking by default",
            subtitle = "New puzzles open with autocheck on, marking wrong letters as you type.",
        ) {
            Switch(
                checked = viewModel.autocheckDefault,
                onCheckedChange = viewModel::updateAutocheckDefault,
            )
        }
    }
}

@Composable
private fun FeedsSection(viewModel: SettingsViewModel, onAddFeed: () -> Unit) {
    SectionCard("Feeds", "Turn sources on or off. Off feeds aren't updated and don't appear in the library's filters.") {
        viewModel.sources.forEachIndexed { i, source ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val isCustom = source.id.startsWith(PuzzleSources.CUSTOM_PREFIX)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (isCustom) "Custom feed" else source.attribution,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (isCustom) {
                    IconButton(onClick = { viewModel.removeCustomFeed(source.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove ${source.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = viewModel.isEnabled(source.id),
                    onCheckedChange = { viewModel.setSourceEnabled(source.id, it) },
                )
            }
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedButton(onClick = onAddFeed) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add custom feed")
            }
        }
    }
}

@Composable
private fun DownloadsSection(viewModel: SettingsViewModel) {
    SectionCard("Downloads") {
        SettingRow(
            title = "Auto-download new puzzles",
            subtitle = "When new puzzles appear in enabled feeds, download them automatically instead of just listing them.",
        ) {
            Switch(
                checked = viewModel.autoDownloadProspective,
                onCheckedChange = viewModel::updateAutoDownloadProspective,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        BulkDownloadRow(viewModel)
    }
}

@Composable
private fun BulkDownloadRow(viewModel: SettingsViewModel) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Download full history", style = MaterialTheme.typography.bodyLarge)
        Text(
            "List every puzzle from all enabled feeds back through their archives, then download them all to this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(12.dp))
        when (val state = viewModel.bulk) {
            BulkState.Idle -> {
                Button(onClick = { viewModel.scanArchives() }) { Text("Scan archives") }
            }
            is BulkState.Scanning -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Scanning feeds (${state.done}/${state.total}) — ${state.found} puzzles found",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            is BulkState.Ready -> {
                if (state.pending == 0) {
                    Text(
                        "Everything available is already downloaded.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.dismissBulk() }) { Text("Done") }
                } else {
                    Text(
                        "${state.pending} puzzles available to download.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.downloadPending() }) {
                            Text("Download ${state.pending}")
                        }
                        TextButton(onClick = { viewModel.dismissBulk() }) { Text("Not now") }
                    }
                }
            }
            is BulkState.Downloading -> {
                val fraction = if (state.total == 0) 0f else state.done.toFloat() / state.total
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Downloading ${state.done} of ${state.total}" +
                        if (state.failed > 0) " — ${state.failed} failed" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.cancelBulk() }) { Text("Stop") }
            }
            is BulkState.Finished -> {
                Text(
                    "Downloaded ${state.downloaded} puzzles" +
                        if (state.failed > 0) " — ${state.failed} couldn't be fetched:" else ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.failures.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    state.failures.entries
                        .sortedByDescending { it.value }
                        .forEach { (reason, count) ->
                            Text(
                                "· $count ${reason.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.retryable > 0) {
                        Button(onClick = { viewModel.downloadPending() }) { Text("Try again") }
                    }
                    TextButton(onClick = { viewModel.dismissBulk() }) { Text("Done") }
                }
                if (state.retryable > 0) {
                    Text(
                        "About ${state.retryable} of these (rate-limits and timeouts) are usually " +
                            "temporary — tap Try again to fetch the rest. Files already downloaded " +
                            "are skipped, so it only retries what's missing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferSection(viewModel: SettingsViewModel) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::exportTo) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restoreFrom) }

    SectionCard(
        "New phone",
        "Back up every downloaded puzzle, your progress and solve times, the " +
            "catalog of published puzzles, and these settings to one file. Save it " +
            "somewhere both phones can reach (Drive, Files, a cable copy), then " +
            "restore it on the new phone.",
    ) {
        if (viewModel.hasApiKey) {
            SettingRow(
                title = "Include Claude API key",
                subtitle = "Off by default: the backup file is plain and unencrypted, so anyone " +
                    "who gets it would get the key. Leave it off and re-enter the key on the new phone.",
            ) {
                Switch(
                    checked = viewModel.includeApiKeyInBackup,
                    onCheckedChange = viewModel::updateIncludeApiKeyInBackup,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when (val state = viewModel.transfer) {
                TransferState.Idle -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch(viewModel.backupFileName) }) {
                            Text("Back up to file")
                        }
                        OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) {
                            Text("Restore")
                        }
                    }
                    Text(
                        "Restoring merges: puzzles already on this phone keep whichever copy " +
                            "is further along, so nothing you have solved is lost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                is TransferState.Exporting -> TransferProgress(
                    label = "Backing up",
                    done = state.done,
                    total = state.total,
                    onCancel = viewModel::cancelTransfer,
                )
                is TransferState.Restoring -> TransferProgress(
                    label = "Restoring",
                    done = state.done,
                    total = state.total,
                    onCancel = viewModel::cancelTransfer,
                )
                is TransferState.Exported -> {
                    Text(
                        "Backed up ${state.result.puzzles} puzzles and " +
                            "${state.result.catalog} catalog entries (${formatSize(state.result.bytes)}).",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Open the file on the new phone, install xwd there, and tap Restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.dismissTransfer() }) { Text("Done") }
                }
                is TransferState.Restored -> {
                    val r = state.result
                    val headline = buildString {
                        append("Restored ${r.puzzlesAdded} puzzles")
                        if (r.puzzlesUpdated > 0) append(", updated ${r.puzzlesUpdated}")
                        append(".")
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val details = buildList {
                        if (r.puzzlesKept > 0) add("${r.puzzlesKept} already up to date")
                        if (r.catalogAdded > 0) add("${r.catalogAdded} new catalog entries")
                        if (r.settingsRestored) add("settings restored")
                        if (r.apiKeyRestored) add("API key restored")
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.dismissTransfer() }) { Text("Done") }
                }
                is TransferState.Failed -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.dismissTransfer() }) { Text("Back") }
                }
            }
        }
    }
}

@Composable
private fun TransferProgress(label: String, done: Int, total: Int, onCancel: () -> Unit) {
    if (total > 0) {
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
    Text(
        if (total > 0) "$label - $done of $total rows" else "$label...",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onCancel) { Text("Stop") }
}

/** Human-readable file size for the backup we just wrote. */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes bytes"
}

@Composable
private fun AddFeedDialog(onDismiss: () -> Unit, onAdd: (String, String) -> String?) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Paste the address of a page that links to .puz or .ipuz files " +
                        "(for example a constructor's blog or downloads page). xwd lists " +
                        "the puzzles it links and downloads them on demand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("Page URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = onAdd(name, url)
                if (err == null) onDismiss() else error = err
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
