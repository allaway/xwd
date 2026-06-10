package com.allaway.xwd.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allaway.xwd.data.PuzzleRepository
import com.allaway.xwd.data.Stats
import com.allaway.xwd.data.StatsCalculator
import com.allaway.xwd.data.XwdDatabase
import kotlinx.coroutines.launch

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PuzzleRepository(XwdDatabase.get(application).puzzleDao())

    var stats: Stats? by mutableStateOf(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            stats = StatsCalculator.compute(repo.totalCount(), repo.completed())
        }
    }
}
