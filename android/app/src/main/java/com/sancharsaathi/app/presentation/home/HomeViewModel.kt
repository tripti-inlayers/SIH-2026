package com.sancharsaathi.app.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.domain.capture.DemoContentSource
import com.sancharsaathi.app.domain.classifier.MessageClassifier
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.CaptureSource
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.domain.model.RiskSignal
import com.sancharsaathi.app.data.remote.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.UUID

sealed interface HomeUiState {
    data class Success(val recentAnalyses: List<RiskResult>) : HomeUiState
    data object Loading : HomeUiState
}

class HomeViewModel(
    private val historyStore: HistoryStore,
    private val demoContentSource: DemoContentSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Listen to live broadcast receiver events to refresh inbox instantly
        viewModelScope.launch {
            com.sancharsaathi.app.domain.capture.SmsCaptureChannel.events.collect {
                refreshInbox()
                delay(500)
                refreshInbox()
                delay(1000)
            }
        }
        // Automatically collect database updates and refresh UI instantly (only SMS messages, deduplicated)
        viewModelScope.launch {
            historyStore.realSmsHistory.collect { detections ->
                val uniqueDetections = detections.distinctBy { 
                    (it.sender?.filter { c -> c.isLetterOrDigit() }?.takeLast(10) ?: "") to (it.smsBody?.trim() ?: "")
                }
                _uiState.value = HomeUiState.Success(uniqueDetections.take(50))
            }
        }

        // Start a periodic background sync loop (every 4 seconds) tied to the viewModelScope lifecycle
        viewModelScope.launch {
            while (true) {
                try {
                    refreshInbox()
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error in sync loop: ${e.message}", e)
                }
                delay(4000)
            }
        }
    }

    fun refreshInbox() {
        viewModelScope.launch(Dispatchers.IO) {
            val inboxReader = AppModule.smsInboxReader
            val smsMessages = inboxReader.getLatestInboxMessages(50)
            
            smsMessages.forEach { msg ->
                val stableId = inboxReader.generateStableId(msg.sender, msg.body, msg.timestamp)
                val cached = historyStore.get(stableId)
                if (cached == null) {
                    // Create and save placeholder to DB instantly
                    val placeholder = RiskResult(
                        analysisId = stableId,
                        riskScore = -2,
                        riskLevel = RiskLevel.LOW,
                        confidence = 0.0,
                        reasons = listOf("Analyzing..."),
                        signals = emptyList(),
                        recommendedAction = "Analyzing message content...",
                        shouldBlock = false,
                        shouldReport = false,
                        detectedUrl = null,
                        sender = msg.sender,
                        modelVersion = "1.0.0",
                        degraded = true,
                        smsBody = msg.body,
                        timestamp = msg.timestamp
                    )
                    historyStore.add(placeholder, source = CaptureSource.SMS, status = "PENDING")
                    analyzeSmsInBackground(stableId, msg.sender, msg.body, msg.timestamp)
                } else if (cached.riskScore == -1) {
                    // Re-trigger analysis
                    analyzeSmsInBackground(stableId, msg.sender, msg.body, msg.timestamp)
                }
            }

            // Immediately emit the latest deduplicated real SMS detections to _uiState
            val latestDetections = historyStore.realSmsHistory.first()
            val uniqueDetections = latestDetections.distinctBy { 
                (it.sender?.filter { c -> c.isLetterOrDigit() }?.takeLast(10) ?: "") to (it.smsBody?.trim() ?: "")
            }
            _uiState.value = HomeUiState.Success(uniqueDetections.take(50))
        }
    }

    private fun analyzeSmsInBackground(analysisId: String, sender: String, body: String, timestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("HomeViewModel", "SMS_ANALYSIS_START: phoneSmsId=$analysisId")
            
            val classification = MessageClassifier.classify(body)
            val urls = extractUrls(body)
            val isLocalMatch = !classification.requiresFallback

            Log.d("HomeViewModel", "phoneSmsId=$analysisId, LOCAL_TEMPLATE_MATCH=$isLocalMatch, ANALYSIS_ROUTE=${if (isLocalMatch) "LOCAL_RULE" else "BACKEND_MODEL"}")

            if (isLocalMatch) {
                val localResult = RiskResult(
                    analysisId = analysisId,
                    riskScore = classification.riskScore,
                    riskLevel = classification.riskLevel,
                    confidence = 0.95,
                    reasons = listOf(classification.reason),
                    signals = classification.triggeredFeatures.map { feature ->
                        RiskSignal(
                            category = "local_template",
                            code = classification.matchedTemplateId ?: "GENERIC_MATCH",
                            description = "Matched pattern feature: $feature",
                            technicalDetail = "Local template regex match",
                            weight = classification.riskScore / 100.0,
                            triggered = true
                        )
                    },
                    recommendedAction = when (classification.riskLevel) {
                        RiskLevel.HIGH -> "Danger: Block link and report immediately."
                        RiskLevel.SUSPICIOUS -> "Suspicious content. Exercise caution."
                        else -> "Looks safe to interact."
                    },
                    shouldBlock = classification.riskLevel == RiskLevel.HIGH,
                    shouldReport = classification.riskLevel == RiskLevel.HIGH,
                    detectedUrl = urls.firstOrNull(),
                    sender = sender,
                    modelVersion = "local-1.0.0",
                    degraded = false,
                    degradedReason = null,
                    smsBody = body,
                    timestamp = timestamp
                )

                historyStore.add(localResult, source = CaptureSource.SMS)
                Log.d("HomeViewModel", "SMS_FINAL_RESULT: phoneSmsId=$analysisId, score=${localResult.riskScore}, level=${localResult.riskLevel}")
            } else {
                // Perform complete on-device security analysis first
                val onDeviceResult = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                    analysisId = analysisId,
                    text = body,
                    sender = sender,
                    timestamp = timestamp,
                    source = CaptureSource.SMS
                )

                // Attempt backend neural model enrichment
                val request = AnalysisRequest(
                    messageId = analysisId,
                    text = body,
                    urls = urls,
                    senderId = sender,
                    claimedOrganization = detectClaimedOrg(body),
                    language = "en",
                    timestampEpochMillis = timestamp,
                    source = CaptureSource.SMS
                )

                Log.d("HomeViewModel", "SMS_BACKEND_ANALYSIS_START: phoneSmsId=$analysisId")
                val netResult = AppModule.analyzeContentUseCase(request)
                
                val finalResult = when (netResult) {
                    is NetworkResult.Success -> {
                        netResult.data.copy(smsBody = body, timestamp = timestamp)
                    }
                    is NetworkResult.Failure -> {
                        // Use on-device engine result with offline indicator
                        onDeviceResult.copy(
                            degraded = true,
                            degradedReason = "offline_on_device_engine"
                        )
                    }
                }

                historyStore.add(finalResult, source = CaptureSource.SMS)
                Log.d("HomeViewModel", "SMS_FINAL_RESULT: phoneSmsId=$analysisId, score=${finalResult.riskScore}, level=${finalResult.riskLevel}")
            }
        }
    }

    private fun extractUrls(text: String): List<String> {
        val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(text).map { it.value }.toList()
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

    fun createManualAnalysisRequest(input: String): AnalysisRequest {
        val trimmed = input.trim()
        val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        val extractedUrls = urlRegex.findAll(trimmed).map { it.value }.toList()

        return AnalysisRequest(
            messageId = "MANUAL-${UUID.randomUUID().toString().take(8)}",
            text = trimmed,
            urls = extractedUrls,
            senderId = if (extractedUrls.contains(trimmed)) "URL_ANALYSIS" else "MANUAL_INPUT",
            claimedOrganization = null,
            language = "en",
            timestampEpochMillis = System.currentTimeMillis(),
            source = CaptureSource.SHARED
        )
    }

    // Retained for developer/test mode
    fun launchDemoScenario(scenarioIndex: Int): AnalysisRequest {
        return demoContentSource.triggerScenario(scenarioIndex)
    }
}
