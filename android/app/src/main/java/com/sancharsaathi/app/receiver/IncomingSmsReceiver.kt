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
                val body = bodyBuilder.toString()
                if (body.isNotBlank()) {
                    processIncomingSms(context, sender, body)
                }
            }
        }
    }

    private fun processIncomingSms(context: Context, sender: String, body: String) {
        val urls = extractUrls(body)
        val timestamp = System.currentTimeMillis()
        val stableAnalysisId = AppModule.smsInboxReader.generateStableId(sender, body, timestamp)

        val analysisRequest = AnalysisRequest(
            messageId = stableAnalysisId,
            text = body,
            urls = urls,
            senderId = sender,
            claimedOrganization = detectClaimedOrg(body),
            language = "en",
            timestampEpochMillis = timestamp,
            source = CaptureSource.SMS
        )

        android.util.Log.d("SancharSaathiSMS", "SMS_RECEIVED_EVENT: sender=$sender, msgId=${analysisRequest.messageId}, length=${body.length}")
        android.util.Log.d("SancharSaathiSMS", "SMS_ANALYSIS_STARTED: msgId=${analysisRequest.messageId}")

        // 1. Run local classifier
        val classification = MessageClassifier.classify(body)

        if (!classification.requiresFallback) {
            // Local match - Save directly and notify
            val localResult = RiskResult(
                analysisId = stableAnalysisId,
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
            
            AppModule.historyStore.add(localResult, source = CaptureSource.SMS)
            android.util.Log.d("SancharSaathiSMS", "SMS_ANALYSIS_COMPLETED: analysisId=${localResult.analysisId}, riskLevel=${localResult.riskLevel}")
            android.util.Log.d("SancharSaathiSMS", "SMS_HISTORY_SAVED: analysisId=${localResult.analysisId}, source=REAL_SMS")
            
            NotificationHelper.showNotification(context, localResult)
            
            // Also notify the capture channel so any active screen updates
            SmsCaptureChannel.emitSms(sender, body)
            receiverScope.launch { AppModule.smsInboxReader.readInboxAndSync(10) }
        } else {
            // 2. No predefined pattern - Fallback to backend analysis via coroutine
            receiverScope.launch {
                val result = AppModule.analyzeContentUseCase(analysisRequest)
                val finalResult = when (result) {
                    is NetworkResult.Success -> {
                        // Ensure we carry forward smsBody and timestamp
                        result.data.copy(smsBody = body, timestamp = timestamp)
                    }
                    is NetworkResult.Failure -> {
                        // Run complete on-device security engine fallback
                        com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                            analysisId = stableAnalysisId,
                            text = body,
                            sender = sender,
                            timestamp = timestamp,
                            source = CaptureSource.SMS
                        ).copy(
                            degraded = true,
                            degradedReason = "backend_unreachable"
                        )
                    }
                }
                AppModule.historyStore.add(finalResult, source = CaptureSource.SMS)
                android.util.Log.d("SancharSaathiSMS", "SMS_ANALYSIS_COMPLETED: analysisId=${finalResult.analysisId}, riskLevel=${finalResult.riskLevel}")
                android.util.Log.d("SancharSaathiSMS", "SMS_HISTORY_SAVED: analysisId=${finalResult.analysisId}, source=REAL_SMS")
                
                NotificationHelper.showNotification(context, finalResult)
                
                // Trigger channel update for active view
                SmsCaptureChannel.emitSms(sender, body)
                AppModule.smsInboxReader.readInboxAndSync(10)
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
}
