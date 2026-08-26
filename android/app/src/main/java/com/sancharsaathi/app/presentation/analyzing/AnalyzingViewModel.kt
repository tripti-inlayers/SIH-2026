package com.sancharsaathi.app.presentation.analyzing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.domain.usecase.AnalyzeContentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalyzingUiState {
    data object Loading : AnalyzingUiState
    data class Success(val result: RiskResult) : AnalyzingUiState
    data class Error(val message: String, val retryable: Boolean) : AnalyzingUiState
}

class AnalyzingViewModel(
    private val analyzeContentUseCase: AnalyzeContentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalyzingUiState>(AnalyzingUiState.Loading)
    val uiState: StateFlow<AnalyzingUiState> = _uiState.asStateFlow()

    fun analyze(request: AnalysisRequest) {
        _uiState.value = AnalyzingUiState.Loading
        viewModelScope.launch {
            when (val result = analyzeContentUseCase(request)) {
                is NetworkResult.Success -> {
                    _uiState.value = AnalyzingUiState.Success(result.data)
                }
                is NetworkResult.Failure -> {
                    _uiState.value = AnalyzingUiState.Error(
                        message = result.message,
                        retryable = true
                    )
                }
            }
        }
    }

    fun getUnverifiedFallbackResult(request: AnalysisRequest): RiskResult {
        return RiskResult(
            analysisId = "UNVERIFIED-${request.messageId}",
            riskScore = 0,
            riskLevel = RiskLevel.LOW,
            confidence = 0.0,
            reasons = listOf("Unverified — Security analysis offline."),
            signals = emptyList(),
            recommendedAction = "Verification unavailable — proceed with caution.",
            shouldBlock = false,
            shouldReport = false,
            detectedUrl = request.urls.firstOrNull(),
            sender = request.senderId,
            modelVersion = "1.0.0",
            degraded = true,
            degradedReason = "backend_unreachable"
        )
    }
}
