package com.sancharsaathi.app.domain.capture

import android.content.Context
import android.util.Log
import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object SmsCaptureManager {

    private const val TAG = "SmsCaptureManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processedSignatures = ConcurrentHashMap<String, Long>()
    private const val DUP_WINDOW_MS = 30_000L

    fun startListening(context: Context) {
        scope.launch {
            Log.i(TAG, "SmsCaptureManager starting application-level Live SMS event collector...")
            SmsCaptureChannel.events.collect { request ->
                val signature = "${request.senderId ?: "UNKNOWN"}:${request.timestampEpochMillis}:${request.text}"
                val now = System.currentTimeMillis()

                // Clean expired entries from window
                processedSignatures.entries.removeIf { now - it.value > DUP_WINDOW_MS }

                if (processedSignatures.containsKey(signature)) {
                    Log.d(TAG, "Skipping duplicate incoming SMS event with signature: $signature")
                    return@collect
                }
                processedSignatures[signature] = now

                Log.i(TAG, "Processing incoming Live SMS id=${request.messageId} via AnalyzeContentUseCase")
                when (val result = AppModule.analyzeContentUseCase(request)) {
                    is NetworkResult.Success -> {
                        val riskResult = result.data
                        Log.i(TAG, "Live SMS analysis completed: level=${riskResult.riskLevel}, score=${riskResult.riskScore}")
                        AppModule.historyStore.add(riskResult)
                        if (riskResult.riskLevel == RiskLevel.HIGH || riskResult.riskLevel == RiskLevel.SUSPICIOUS) {
                            NotificationHelper.showThreatNotification(context, riskResult)
                        }
                    }
                    is NetworkResult.Failure -> {
                        Log.e(TAG, "Live SMS analysis failed: ${result.message} (reason: ${result.reason})")
                    }
                }
            }
        }
    }
}
