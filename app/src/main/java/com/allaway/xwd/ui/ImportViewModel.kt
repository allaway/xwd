package com.allaway.xwd.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allaway.xwd.data.PuzzleRepository
import com.allaway.xwd.data.Settings
import com.allaway.xwd.data.XwdDatabase
import com.allaway.xwd.import_.ImportConverter
import com.allaway.xwd.import_.ImportException
import com.allaway.xwd.import_.PhotoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface ImportState {
    data object Idle : ImportState
    data object Working : ImportState
    data class Failed(val message: String) : ImportState
    data class Done(val puzzleId: String, val title: String, val warnings: List<String>) : ImportState
}

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PuzzleRepository(XwdDatabase.get(application).puzzleDao())

    var apiKey: String by mutableStateOf(Settings.getApiKey(application))
        private set
    var state: ImportState by mutableStateOf(ImportState.Idle)
        private set

    fun saveApiKey(value: String) {
        Settings.setApiKey(getApplication(), value)
        apiKey = value.trim()
    }

    fun reset() {
        if (state !is ImportState.Working) state = ImportState.Idle
    }

    fun importImage(uri: Uri) {
        if (state is ImportState.Working) return
        val key = apiKey
        if (key.isBlank()) {
            state = ImportState.Failed("Add your Claude API key first.")
            return
        }
        state = ImportState.Working
        viewModelScope.launch {
            state = try {
                val jpeg = withContext(Dispatchers.IO) { prepareImage(uri) }
                val imported = withContext(Dispatchers.IO) { PhotoImporter(key).import(jpeg) }
                val converted = ImportConverter.convert(imported)
                val entity = repo.storeImported(converted.puzzle)
                ImportState.Done(entity.id, converted.puzzle.title, converted.warnings)
            } catch (e: ImportException) {
                ImportState.Failed(e.message ?: "Import failed.")
            } catch (e: Exception) {
                ImportState.Failed("Import failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Decode, downscale to [MAX_EDGE] px on the long edge, re-encode as JPEG. */
    private fun prepareImage(uri: Uri): ByteArray {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw ImportException("Couldn't open the selected image.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImportException("The selected file is not a readable image.")
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw ImportException("Couldn't decode the selected image.")
        val longEdge = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longEdge > MAX_EDGE) {
            val factor = MAX_EDGE.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * factor).toInt().coerceAtLeast(1),
                (bitmap.height * factor).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return out.toByteArray()
    }

    companion object {
        /** Opus supports up to 2576px on the long edge; stay under it. */
        private const val MAX_EDGE = 2300
    }
}
