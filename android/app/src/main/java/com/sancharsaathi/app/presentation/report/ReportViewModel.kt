package com.sancharsaathi.app.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.domain.model.ThreatReport
import com.sancharsaathi.app.domain.usecase.SubmitReportUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReportUiState {
    data object Loading : ReportUiState
    data class Success(val report: ThreatReport) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

class ReportViewModel(
    private val submitReportUseCase: SubmitReportUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun submitReport(riskResult: RiskResult) {
        _uiState.value = ReportUiState.Loading
        viewModelScope.launch {
            when (val res = submitReportUseCase(riskResult)) {
                is NetworkResult.Success -> {
                    _uiState.value = ReportUiState.Success(res.data)
                }
                is NetworkResult.Failure -> {
                    _uiState.value = ReportUiState.Error(res.message)
                }
            }
        }
    }
}
