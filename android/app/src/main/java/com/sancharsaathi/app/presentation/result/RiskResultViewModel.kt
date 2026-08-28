package com.sancharsaathi.app.presentation.result

import androidx.lifecycle.ViewModel
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.domain.model.RiskResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RiskResultUiState {
    data object Loading : RiskResultUiState
    data class Success(val result: RiskResult) : RiskResultUiState
    data class Error(val message: String, val retryable: Boolean) : RiskResultUiState
}

class RiskResultViewModel(
    private val historyStore: HistoryStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<RiskResultUiState>(RiskResultUiState.Loading)
    val uiState: StateFlow<RiskResultUiState> = _uiState.asStateFlow()

    fun loadResult(analysisId: String, preloadedResult: RiskResult? = null) {
        if (preloadedResult != null && preloadedResult.analysisId == analysisId) {
            _uiState.value = RiskResultUiState.Success(preloadedResult)
            return
        }

        val cached = historyStore.get(analysisId)
            ?: historyStore.get(if (analysisId.startsWith("SMS-")) analysisId.removePrefix("SMS-") else "SMS-$analysisId")

        if (cached != null) {
            _uiState.value = RiskResultUiState.Success(cached)
        } else {
            _uiState.value = RiskResultUiState.Error("Analysis record not found.", retryable = true)
        }
    }
}
