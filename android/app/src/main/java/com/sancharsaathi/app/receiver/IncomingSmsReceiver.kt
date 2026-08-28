package com.sancharsaathi.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.domain.classifier.MessageClassifier
import com.sancharsaathi.app.domain.model.*
import com.sancharsaathi.app.domain.capture.SmsCaptureChannel
import com.sancharsaathi.app.data.remote.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class IncomingSmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (!messages.isNullOrEmpty()) {
                val sender = messages[0].displayOriginatingAddress ?: "Unknown"
                val bodyBuilder = StringBuilder()
                for (msg in messages) {
                    bodyBuilder.append(msg.displayMessageBody)
                }
                val timestamp = messages[0].timestampMillis.let { if (it > 0) it else System.currentTimeMillis() }
                val body = bodyBuilder.toString()
                if (body.isNotBlank()) {
                    android.util.Log.d("SancharSaathiSms", "[RECEIVED] -1 | $sender | $timestamp")
                    SmsCaptureChannel.emitSms(sender, body)
                    processIncomingSms(context, sender, body, timestamp)
                }
            }
        }
    }

    private fun processIncomingSms(context: Context, sender: String, body: String, timestamp: Long) {
        val urls = extractUrls(body)

        receiverScope.launch {
            // Wait 500ms for system to persist the message to content://sms
            kotlinx.coroutines.delay(500)
            
            var providerId = AppModule.smsInboxReader.findProviderId(sender, body)
            if (providerId == null) {
                // Try again after another 500ms
                kotlinx.coroutines.delay(500)
                providerId = AppModule.smsInboxReader.findProviderId(sender, body)
            }
            
            // If still null, generate a fallback stable ID
            val phoneIdKey = if (providerId != null) {
                "SMS-$providerId"
            } else {
                AppModule.smsInboxReader.generateStableId(sender, body, timestamp)
            }
            
            android.util.Log.d("SancharSaathiSms", "[RECEIVED] providerId=$providerId phoneIdKey=$phoneIdKey")
            
            // 1. Create a persistent pending record in HistoryStore so UI displays it immediately
            val pendingResult = RiskResult(
                analysisId = phoneIdKey,
                riskScore = -2, // Triggers PENDING / ANALYZING state
                riskLevel = RiskLevel.LOW,
                confidence = 0.0,
                reasons = listOf("Analyzing message content..."),
                signals = emptyList(),
                recommendedAction = "Analyzing message content...",
                shouldBlock = false,
                shouldReport = false,
                detectedUrl = urls.firstOrNull(),
                sender = sender,
                modelVersion = "1.0.0",
                degraded = true,
                smsBody = body,
                timestamp = timestamp
            )
            AppModule.historyStore.add(pendingResult, source = CaptureSource.SMS, status = "PENDING")
            
            // Instantly notify live screens via channel so feed shows the placeholder
            SmsCaptureChannel.emitSms(sender, body)
            
            // 2. Perform analysis
            val classification = MessageClassifier.classify(body)
            val isLocalMatch = !classification.requiresFallback
            val shouldCallBackend = !isLocalMatch || urls.isNotEmpty()
            
            val finalResult: RiskResult
            if (!shouldCallBackend) {
                // Local match without URL - complete offline
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
                            description = "Matched local pattern feature: $feature",
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
                // Backend analysis request
                val analysisRequest = AnalysisRequest(
                    messageId = phoneIdKey,
                    text = body,
                    urls = urls,
                    senderId = sender,
                    claimedOrganization = detectClaimedOrg(body),
                    language = "en",
                    timestampEpochMillis = timestamp,
                    source = CaptureSource.SMS
                )
                
                android.util.Log.d("SancharSaathiSms", "[ANALYSIS_START] $phoneIdKey")
                val netResult = try {
                    kotlinx.coroutines.withTimeout(8000L) {
                        AppModule.analyzeContentUseCase(analysisRequest)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.e("SancharSaathiSms", "Backend analysis timed out after 8s for $phoneIdKey")
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
                        // Fallback to on-device engine
                        val onDeviceResult = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                            analysisId = phoneIdKey,
                            text = body,
                            sender = sender,
                            timestamp = timestamp,
                            source = CaptureSource.SMS
                        )
                        onDeviceResult.copy(
                            analysisId = phoneIdKey,
                            degraded = true,
                            degradedReason = "backend_unreachable (${netResult.message})"
                        )
                    }
                }
            }
            
            // Save final result
            val statusToSave = if (finalResult.riskScore == -1) "FAILED" else "COMPLETED"
            AppModule.historyStore.add(finalResult, source = CaptureSource.SMS, status = statusToSave)
            
            if (finalResult.riskScore == -1) {
                android.util.Log.d("SancharSaathiSms", "[ANALYSIS_FAILURE] $phoneIdKey | ${finalResult.degradedReason ?: "failed"}")
            } else {
                android.util.Log.d("SancharSaathiSms", "[ANALYSIS_SUCCESS] $phoneIdKey | ${finalResult.riskScore} | ${finalResult.riskLevel}")
            }
            
            NotificationHelper.showNotification(context, finalResult)
            SmsCaptureChannel.emitSms(sender, body)
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
}
