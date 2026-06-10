package com.allaway.xwd.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allaway.xwd.data.PuzzleEntity
import com.allaway.xwd.data.PuzzleRepository
import com.allaway.xwd.data.XwdDatabase
import com.allaway.xwd.sources.PuzzleSource
import com.allaway.xwd.sources.PuzzleSources
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PuzzleRepository(XwdDatabase.get(application).puzzleDao())

    val puzzles: StateFlow<List<PuzzleEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var downloading: Boolean by mutableStateOf(false)
        private set
    var message: String? by mutableStateOf(null)

    val sources: List<PuzzleSource> = PuzzleSources.all

    /** Sources with a browseable per-date archive. */
    val datedSources: List<PuzzleSource> = PuzzleSources.dated

    /** Fetch the newest available puzzle from every source. */
    fun downloadLatestFromAll() {
        if (downloading) return
        downloading = true
        viewModelScope.launch {
            val added = ArrayList<String>()
            val errors = ArrayList<String>()
            for (source in sources) {
                try {
                    repo.downloadLatest(source)?.let { added.add("${source.name} ${it.date}") }
                } catch (e: Exception) {
                    errors.add("${source.name}: ${e.message ?: "download failed"}")
                }
            }
            message = when {
                errors.isNotEmpty() -> errors.joinToString("\n")
                added.isEmpty() -> "You're all caught up — no new puzzles."
                else -> "Added ${added.joinToString(", ")}"
            }
            downloading = false
        }
    }

    /** Fetch a specific date from one source (e.g. picked from the archive dialog). */
    fun downloadFor(source: PuzzleSource, epochMillis: Long) {
        if (downloading) return
        downloading = true
        viewModelScope.launch {
            val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            message = try {
                val entity = repo.downloadFor(source, date)
                if (entity != null) "Added ${source.name} $date"
                else "${source.name} has no puzzle for $date"
            } catch (e: Exception) {
                "${source.name}: ${e.message ?: "download failed"}"
            }
            downloading = false
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }
}
