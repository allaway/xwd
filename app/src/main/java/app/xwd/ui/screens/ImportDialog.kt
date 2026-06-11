package app.xwd.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.xwd.ui.ImportState
import app.xwd.ui.ImportViewModel
import java.io.File

/**
 * Import-from-photo flow: pick or take a picture of a crossword; Claude reads
 * and solves it behind the scenes; the result lands in the library.
 */
@Composable
fun ImportDialog(
    viewModel: ImportViewModel,
    onDismiss: () -> Unit,
    onOpenPuzzle: (String) -> Unit,
) {
    val context = LocalContext.current
    var keyDraft by rememberSaveable { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.importImage(it) } }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved -> if (saved) cameraUri?.let { viewModel.importImage(it) } }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "import-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    val state = viewModel.state
    AlertDialog(
        onDismissRequest = { if (state !is ImportState.Working) onDismiss() },
        title = { Text("Import from photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is ImportState.Working -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Reading and solving the puzzle… This can take a few " +
                                    "minutes for a hard crossword.",
                            )
                        }
                    }
                    is ImportState.Failed -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        PickerButtons(onCamera = ::launchCamera, onGallery = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        ApiKeySection(viewModel, keyDraft, { keyDraft = it })
                    }
                    is ImportState.Done -> {
                        Text("“${state.title}” was imported and solved.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "The solution was reconstructed by AI from your photo — " +
                                "check and reveal compare against it, and it may contain errors.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.warnings.isNotEmpty()) {
                            Text(
                                "Possible issues:\n" + state.warnings.take(5).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    is ImportState.Idle -> {
                        Text(
                            "Take a picture or choose a screenshot of a crossword. " +
                                "Claude reads the grid and clues, solves the puzzle, and " +
                                "adds it to your library so you can solve it yourself.",
                        )
                        PickerButtons(onCamera = ::launchCamera, onGallery = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        ApiKeySection(viewModel, keyDraft, { keyDraft = it })
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is ImportState.Done -> TextButton(onClick = {
                    val id = state.puzzleId
                    viewModel.reset()
                    onDismiss()
                    onOpenPuzzle(id)
                }) { Text("Solve it") }
                else -> {}
            }
        },
        dismissButton = {
            if (state !is ImportState.Working) {
                TextButton(onClick = {
                    viewModel.reset()
                    onDismiss()
                }) { Text(if (state is ImportState.Done) "Later" else "Cancel") }
            }
        },
    )
}

@Composable
private fun PickerButtons(onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onCamera, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("  Camera")
        }
        Button(onClick = onGallery, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Text("  Gallery")
        }
    }
}

@Composable
private fun ApiKeySection(
    viewModel: ImportViewModel,
    draft: String,
    onDraftChange: (String) -> Unit,
) {
    if (viewModel.apiKey.isBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Importing uses the Claude API and needs your own API key " +
                    "(console.anthropic.com). It is stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text("Claude API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { viewModel.saveApiKey(draft) },
                enabled = draft.isNotBlank(),
            ) { Text("Save key") }
        }
    } else {
        TextButton(onClick = { viewModel.saveApiKey("") }) {
            Text("Clear saved API key", style = MaterialTheme.typography.bodySmall)
        }
    }
}
