package com.sancharsaathi.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.domain.capture.DemoContentSource
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.RiskResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data class Success(val recentAnalyses: List<RiskResult>) : HomeUiState
    data object Loading : HomeUiState
}

class HomeViewModel(
    private val historyStore: HistoryStore,
    private val demoContentSource: DemoContentSource
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = historyStore.history
        .map { list -> HomeUiState.Success(list.take(3)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun launchDemoScenario(scenarioIndex: Int): AnalysisRequest {
        return demoContentSource.triggerScenario(scenarioIndex)
    }
}
