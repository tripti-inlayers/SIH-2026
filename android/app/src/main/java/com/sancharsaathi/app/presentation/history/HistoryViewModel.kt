package com.sancharsaathi.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.domain.model.RiskResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(
    historyStore: HistoryStore
) : ViewModel() {

    val historyState: StateFlow<List<RiskResult>> = historyStore.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
