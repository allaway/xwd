package com.allaway.xwd.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.allaway.xwd.data.PuzzleEntity
import com.allaway.xwd.data.PuzzleRepository
import com.allaway.xwd.data.XwdDatabase
import com.allaway.xwd.model.Clue
import com.allaway.xwd.model.Direction
import com.allaway.xwd.model.Puzzle
import com.allaway.xwd.model.opposite
import kotlinx.coroutines.launch

enum class CompletionState { IN_PROGRESS, FILLED_INCORRECT, SOLVED }

class SolveViewModel(application: Application, private val puzzleId: String) :
    AndroidViewModel(application) {

    private val repo = PuzzleRepository(XwdDatabase.get(application).puzzleDao())

    var puzzle: Puzzle? by mutableStateOf(null)
        private set
    var letters: String by mutableStateOf("")
        private set
    var selected: Int by mutableStateOf(0)
        private set
    var direction: Direction by mutableStateOf(Direction.ACROSS)
        private set
    var autocheck: Boolean by mutableStateOf(false)
        private set
    var checkedWrong: Set<Int> by mutableStateOf(emptySet())
        private set
    var revealed: Set<Int> by mutableStateOf(emptySet())
        private set
    var elapsedSeconds: Long by mutableStateOf(0L)
        private set
    var completionState: CompletionState by mutableStateOf(CompletionState.IN_PROGRESS)
        private set
    var showCompletionDialog: Boolean by mutableStateOf(false)

    /** True for puzzles imported from a photo, whose solution was AI-reconstructed. */
    var isPhotoImport: Boolean by mutableStateOf(false)
        private set

    private var entity: PuzzleEntity? = null
    private var dismissedIncorrectFill = false

    init {
        viewModelScope.launch {
            val e = repo.get(puzzleId) ?: return@launch
            entity = e
            isPhotoImport = e.sourceId == "photo"
            val p = repo.decode(e)
            puzzle = p
            letters = e.progress
            elapsedSeconds = e.elapsedSeconds
            autocheck = e.autocheckUsed
            if (e.isCompleted) completionState = CompletionState.SOLVED
            selected = p.cells.indexOfFirst { !it.isBlock }.coerceAtLeast(0)
            if (!p.hasWord(selected, direction)) direction = direction.opposite()
        }
    }

    val currentClue: Clue?
        get() = puzzle?.clueAt(selected, direction)

    val currentWord: List<Int>
        get() = currentClue?.cells ?: emptyList()

    fun isWrong(index: Int): Boolean {
        val p = puzzle ?: return false
        val ch = letters.getOrNull(index) ?: return false
        if (ch == '-' || ch == '.') return false
        val correct = p.cells[index].solution ?: return false
        val wrong = ch != correct
        return wrong && (autocheck || index in checkedWrong)
    }

    fun selectCell(index: Int) {
        val p = puzzle ?: return
        if (p.cells[index].isBlock) return
        if (index == selected) {
            val flipped = direction.opposite()
            if (p.hasWord(index, flipped)) direction = flipped
        } else {
            selected = index
            if (!p.hasWord(index, direction)) direction = direction.opposite()
        }
    }

    fun selectClue(clue: Clue) {
        direction = clue.direction
        selected = clue.cells.firstOrNull { letters[it] == '-' } ?: clue.cells.first()
    }

    fun nextClue() = puzzle?.let { p -> currentClue?.let { selectClue(p.nextClue(it)) } }

    fun previousClue() = puzzle?.let { p -> currentClue?.let { selectClue(p.previousClue(it)) } }

    fun input(char: Char) {
        if (completionState == CompletionState.SOLVED) return
        val p = puzzle ?: return
        val cell = selected
        setLetter(cell, char.uppercaseChar())
        entity = entity?.let { it.copy(firstFillCell = it.firstFillCell ?: cell, lastFillCell = cell) }
        advanceAfterInput(p)
        checkCompletion()
        save()
    }

    fun backspace() {
        if (completionState == CompletionState.SOLVED) return
        val p = puzzle ?: return
        if (letters[selected] != '-') {
            setLetter(selected, '-')
        } else {
            val word = currentWord
            val pos = word.indexOf(selected)
            if (pos > 0) {
                selected = word[pos - 1]
                setLetter(selected, '-')
            }
        }
        dismissedIncorrectFill = false
        save()
    }

    private fun setLetter(index: Int, char: Char) {
        if (index in revealed && char == '-') return // revealed letters stay
        letters = letters.substring(0, index) + char + letters.substring(index + 1)
        if (index in checkedWrong) checkedWrong = checkedWrong - index
        if (char == '-') dismissedIncorrectFill = false
    }

    private fun advanceAfterInput(p: Puzzle) {
        val word = currentWord
        val pos = word.indexOf(selected)
        // Next empty cell later in the word, else next cell, else next clue.
        val nextEmpty = word.drop(pos + 1).firstOrNull { letters[it] == '-' }
        when {
            nextEmpty != null -> selected = nextEmpty
            pos + 1 < word.size -> selected = word[pos + 1]
            word.any { letters[it] == '-' } -> selected = word.first { letters[it] == '-' }
            else -> currentClue?.let { clue ->
                if (letters.contains('-')) selectClue(p.nextClue(clue))
            }
        }
    }

    fun toggleAutocheck() {
        autocheck = !autocheck
        if (autocheck) markAutocheckUsed()
        save()
    }

    fun checkLetter() = check(listOf(selected))

    fun checkWord() = check(currentWord)

    fun checkPuzzle() = check(puzzle?.cells?.indices?.toList() ?: emptyList())

    private fun check(indices: List<Int>) {
        val p = puzzle ?: return
        if (p.scrambled) return
        val wrong = indices.filter { i ->
            val cell = p.cells[i]
            !cell.isBlock && letters[i] != '-' && letters[i] != cell.solution
        }
        checkedWrong = checkedWrong + wrong
        entity = entity?.copy(checkCount = (entity?.checkCount ?: 0) + 1)
        save()
    }

    fun revealLetter() = reveal(listOf(selected))

    fun revealWord() = reveal(currentWord)

    fun revealPuzzle() = reveal(puzzle?.cells?.indices?.toList() ?: emptyList())

    private fun reveal(indices: List<Int>) {
        val p = puzzle ?: return
        if (p.scrambled) return
        var changed = false
        val sb = StringBuilder(letters)
        for (i in indices) {
            val solution = p.cells[i].solution ?: continue
            if (sb[i] != solution) {
                sb[i] = solution
                changed = true
            }
            revealed = revealed + i
        }
        if (changed) {
            letters = sb.toString()
            checkedWrong = checkedWrong - indices.toSet()
        }
        entity = entity?.copy(revealCount = (entity?.revealCount ?: 0) + 1)
        checkCompletion()
        save()
    }

    fun clearPuzzle() {
        val p = puzzle ?: return
        letters = buildString { p.cells.forEach { append(if (it.isBlock) '.' else '-') } }
        checkedWrong = emptySet()
        revealed = emptySet()
        dismissedIncorrectFill = false
        if (completionState != CompletionState.SOLVED) {
            completionState = CompletionState.IN_PROGRESS
            entity = entity?.copy(firstFillCell = null, lastFillCell = null)
        }
        save()
    }

    fun tick() {
        if (completionState == CompletionState.SOLVED) return
        elapsedSeconds++
        if (elapsedSeconds % 10 == 0L) save()
    }

    fun dismissCompletionDialog() {
        showCompletionDialog = false
        dismissedIncorrectFill = true
    }

    private fun markAutocheckUsed() {
        entity = entity?.copy(autocheckUsed = true)
    }

    private fun checkCompletion() {
        val p = puzzle ?: return
        if (completionState == CompletionState.SOLVED) return
        if (letters.contains('-')) {
            completionState = CompletionState.IN_PROGRESS
            return
        }
        val allCorrect = !p.scrambled && p.cells.withIndex().all { (i, cell) ->
            cell.isBlock || letters[i] == cell.solution
        }
        if (allCorrect) {
            completionState = CompletionState.SOLVED
            entity = entity?.copy(completedAt = System.currentTimeMillis())
            showCompletionDialog = true
        } else if (!p.scrambled) {
            completionState = CompletionState.FILLED_INCORRECT
            if (!dismissedIncorrectFill) showCompletionDialog = true
        }
    }

    fun save() {
        val e = entity ?: return
        val updated = e.copy(
            progress = letters,
            elapsedSeconds = elapsedSeconds,
            autocheckUsed = e.autocheckUsed || autocheck,
        )
        entity = updated
        viewModelScope.launch { repo.update(updated) }
    }

    override fun onCleared() {
        save()
        super.onCleared()
    }

    class Factory(private val application: Application, private val puzzleId: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SolveViewModel(application, puzzleId) as T
    }
}
