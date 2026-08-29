package com.sancharsaathi.app.presentation.home

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.data.local.PhoneSmsMessage
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    private val inFlightAnalyses = ConcurrentHashMap.newKeySet<String>()

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d("HomeViewModel", "SMS_CONTENT_OBSERVER_TRIGGERED: uri=$uri")
            refreshInbox()
        }
    }

    init {
        // 1. Eagerly warm up backend connection on startup
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetUrl = AppModule.networkConfigStore.getBaseUrl()
                val health = AppModule.apiService.health()
                Log.d("HomeViewModel", "HOME_STARTUP_HEALTH: Connected to $targetUrl (status=${health.body()?.status})")
            } catch (e: Exception) {
                Log.d("HomeViewModel", "HOME_STARTUP_HEALTH: Warmup ping (${e.message})")
            }
        }

        // 2. Register real-time ContentObserver on Android Telephony SMS provider
        try {
            AppModule.appContext.contentResolver.registerContentObserver(
                Uri.parse("content://sms"),
                true,
                smsObserver
            )
            Log.d("HomeViewModel", "SMS_CONTENT_OBSERVER_REGISTERED")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Failed to register ContentObserver: ${e.message}")
        }

        // 3. Listen to live broadcast receiver events
        viewModelScope.launch {
            com.sancharsaathi.app.domain.capture.SmsCaptureChannel.events.collect {
                Log.d("HomeViewModel", "NEW_SMS_DETECTED=true FEED_REFRESH_TRIGGERED=true")
                refreshInbox()
            }
        }

        // 4. Initial load of phone SMS inbox
        refreshInbox()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            AppModule.appContext.contentResolver.unregisterContentObserver(smsObserver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun refreshInbox() {
        viewModelScope.launch(Dispatchers.IO) {
            val inboxReader = AppModule.smsInboxReader
            val phoneMessages = inboxReader.getLatestInboxMessages(10)

            if (phoneMessages.isEmpty()) {
                _uiState.value = HomeUiState.Success(emptyList())
                return@launch
            }

            val feedItems = mutableListOf<RiskResult>()

            phoneMessages.forEach { msg ->
                val phoneIdKey = "SMS-${msg.id}"
                val cached = historyStore.get(phoneIdKey)

                if (cached != null && cached.riskScore != -2) {
                    // Completed analysis found
                    feedItems.add(
                        cached.copy(
                            analysisId = phoneIdKey,
                            sender = msg.sender,
                            smsBody = msg.body,
                            timestamp = msg.timestamp
                        )
                    )
                } else {
                    // Pending or new item: display "Analyzing..." card immediately
                    val placeholder = cached ?: RiskResult(
                        analysisId = phoneIdKey,
                        riskScore = -2, // Triggers "Analyzing..." badge in RiskBadge
                        riskLevel = RiskLevel.LOW,
                        confidence = 0.0,
                        reasons = listOf("Analyzing message content..."),
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
                    feedItems.add(placeholder)

                    // If not already being analyzed in background, persist placeholder and trigger analysis
                    if (inFlightAnalyses.add(phoneIdKey)) {
                        if (cached == null) {
                            historyStore.add(placeholder, source = CaptureSource.SMS, status = "PENDING")
                            Log.d("SancharSaathiSms", "[INSERT] $phoneIdKey | ${msg.id}")
                        }
                        analyzeSmsInBackground(phoneIdKey, msg.id, msg.sender, msg.body, msg.timestamp)
                    }
                }
            }

            // Emit the exact top 10 latest phone SMS to UI
            _uiState.value = HomeUiState.Success(feedItems.take(10))
            Log.d("SancharSaathiSms", "[UI_REFRESH] ${feedItems.take(10).size} records emitted")
        }
    }

    private fun analyzeSmsInBackground(
        phoneIdKey: String,
        phoneSmsId: Long,
        sender: String,
        body: String,
        timestamp: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("SancharSaathiSms", "[ANALYSIS_START] $phoneIdKey")

                val classification = MessageClassifier.classify(body)
                val urls = extractUrls(body)
                val isLocalMatch = !classification.requiresFallback
                val shouldCallBackend = !isLocalMatch || urls.isNotEmpty()

                val finalResult: RiskResult
                if (!shouldCallBackend) {
                    finalResult = RiskResult(
                        analysisId = phoneIdKey,
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
                } else {
                    // On-device security engine baseline
                    val onDeviceResult = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                        analysisId = phoneIdKey,
                        text = body,
                        sender = sender,
                        timestamp = timestamp,
                        source = CaptureSource.SMS
                    )

                    val request = AnalysisRequest(
                        messageId = phoneIdKey,
                        text = body,
                        urls = urls,
                        senderId = sender,
                        claimedOrganization = detectClaimedOrg(body),
                        language = "en",
                        timestampEpochMillis = timestamp,
                        source = CaptureSource.SMS
                    )

                    val netResult = try {
                        withTimeout(8000L) {
                            AppModule.analyzeContentUseCase(request)
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.e("SancharSaathiSms", "Backend analysis timed out after 8s for $phoneIdKey")
                        NetworkResult.Failure(
                            reason = com.sancharsaathi.app.data.remote.FailureReason.TIMEOUT,
                            message = "Analysis timed out after 8s"
                        )
                    } catch (e: Exception) {
                        NetworkResult.Failure(
                            reason = com.sancharsaathi.app.data.remote.FailureReason.UNKNOWN,
                            message = e.message ?: "Unknown error"
                        )
                    }

                    finalResult = when (netResult) {
                        is NetworkResult.Success -> {
                            netResult.data.copy(
                                analysisId = phoneIdKey,
                                smsBody = body,
                                timestamp = timestamp
                            )
                        }
                        is NetworkResult.Failure -> {
                            onDeviceResult.copy(
                                analysisId = phoneIdKey,
                                degraded = true,
                                degradedReason = "backend_unreachable (${netResult.message})"
                            )
                        }
                    }
                }

                val statusToSave = if (finalResult.riskScore == -1) "FAILED" else "COMPLETED"
                historyStore.add(finalResult, source = CaptureSource.SMS, status = statusToSave)
                if (finalResult.riskScore == -1) {
                    Log.d("SancharSaathiSms", "[ANALYSIS_FAILURE] $phoneIdKey | ${finalResult.degradedReason ?: "failed"}")
                } else {
                    Log.d("SancharSaathiSms", "[ANALYSIS_SUCCESS] $phoneIdKey | ${finalResult.riskScore} | ${finalResult.riskLevel}")
                }

                refreshInbox()
            } catch (e: Exception) {
                Log.e("SancharSaathiSms", "[ANALYSIS_FAILURE] $phoneIdKey | ${e.message}", e)
                val fallback = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                    analysisId = phoneIdKey,
                    text = body,
                    sender = sender,
                    timestamp = timestamp,
                    source = CaptureSource.SMS
                ).copy(
                    analysisId = phoneIdKey,
                    degraded = true,
                    degradedReason = "error_fallback (${e.message})"
                )
                historyStore.add(fallback, source = CaptureSource.SMS, status = "COMPLETED")
                refreshInbox()
            } finally {
                inFlightAnalyses.remove(phoneIdKey)
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
