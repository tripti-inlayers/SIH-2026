package com.sancharsaathi.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.domain.capture.DemoContentSource
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.CaptureSource
import com.sancharsaathi.app.domain.model.RiskResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

sealed interface HomeUiState {
    data class Success(val recentAnalyses: List<RiskResult>) : HomeUiState
    data object Loading : HomeUiState
}

class HomeViewModel(
    private val historyStore: HistoryStore,
    private val demoContentSource: DemoContentSource
) : ViewModel() {

    private val _manualInputText = MutableStateFlow("")
    val manualInputText: StateFlow<String> = _manualInputText.asStateFlow()

    val uiState: StateFlow<HomeUiState> = historyStore.history
        .map { list -> HomeUiState.Success(list.take(10)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun onManualInputTextChange(newText: String) {
        _manualInputText.value = newText
    }

    fun buildManualAnalysisRequest(text: String): AnalysisRequest? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val urls = extractUrls(trimmed)
        return AnalysisRequest(
            messageId = "MANUAL-${UUID.randomUUID().toString().take(8)}",
            text = trimmed,
            urls = urls,
            senderId = "User Input",
            claimedOrganization = detectClaimedOrg(trimmed),
            language = "en",
            timestampEpochMillis = System.currentTimeMillis(),
            source = CaptureSource.SHARED
        )
    }

    fun launchDemoScenario(scenarioIndex: Int): AnalysisRequest {
        return demoContentSource.triggerScenario(scenarioIndex)
    }

    private fun extractUrls(text: String): List<String> {
        val urlRegex = Regex("""(?:https?://|cutt\.ly/|bit\.ly/|tinyurl\.com/|t\.co/|(?:[a-zA-Z0-9-]+\.)+(?:com|ly|in|org|net|xyz|tk|top|io|co|gov|edu)/)[^\s]+""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(text).map { match ->
            val raw = match.value
            if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                "https://$raw"
            } else {
                raw
            }
        }.toList()
    }

    private fun detectClaimedOrg(text: String): String? {
        val lower = text.lowercase()
        return when {
            "sbi" in lower || "state bank" in lower -> "State Bank"
            "indiapost" in lower || "post" in lower -> "India Post"
            "irctc" in lower -> "IRCTC"
            "hdfc" in lower -> "HDFC Bank"
            "courier" in lower || "package" in lower -> "Courier Service"
            else -> null
        }
    }
}
