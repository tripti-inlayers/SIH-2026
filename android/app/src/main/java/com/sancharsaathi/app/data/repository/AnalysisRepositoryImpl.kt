package com.sancharsaathi.app.data.repository

import com.sancharsaathi.app.data.local.HistoryStore
import com.sancharsaathi.app.data.remote.AnalysisApiService
import com.sancharsaathi.app.data.remote.FailureReason
import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.data.remote.dto.AnalyzeRequestDto
import com.sancharsaathi.app.data.remote.dto.AnalyzeResponseDto
import com.sancharsaathi.app.domain.model.*
import java.io.IOException
import java.net.SocketTimeoutException

class AnalysisRepositoryImpl(
    private val apiService: AnalysisApiService,
    private val historyStore: HistoryStore
) : AnalysisRepository {

    override suspend fun analyzeContent(request: AnalysisRequest): NetworkResult<RiskResult> {
        android.util.Log.d("SancharSaathiAnalysis", "ANALYSIS_START: msgId=${request.messageId}, sender=${request.senderId}, source=${request.source.name}, hasUrl=${request.urls.isNotEmpty()}")
        return try {
            val dtoRequest = AnalyzeRequestDto(
                messageId = request.messageId,
                text = request.text,
                urls = request.urls,
                senderId = request.senderId,
                claimedOrganization = request.claimedOrganization,
                language = request.language,
                timestampEpochMillis = request.timestampEpochMillis,
                source = request.source.name
            )
            android.util.Log.d("SancharSaathiAnalysis", "BACKEND_ANALYSIS_STARTED: endpoint=/api/v1/analyze, requestCreated=true")
            val response = apiService.analyze(dtoRequest)
            android.util.Log.d("SancharSaathiAnalysis", "BACKEND_ANALYSIS_HTTP_STATUS=${response.code()}")
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                android.util.Log.d("SancharSaathiAnalysis", "BACKEND_RAW_RESPONSE_RECEIVED=true")
                android.util.Log.d("SancharSaathiAnalysis", "BACKEND_SCORE=${dto.riskScore}, BACKEND_RISK_LEVEL=${dto.riskLevel}, BACKEND_CONFIDENCE=${dto.confidence}, BACKEND_SIGNALS_COUNT=${dto.signals.size}")
                val result = mapDtoToDomain(dto)
                android.util.Log.d("SancharSaathiAnalysis", "FINAL_RISK_SCORE=${result.riskScore}, FINAL_RISK_LEVEL=${result.riskLevel}")
                historyStore.add(result, source = request.source)
                NetworkResult.Success(result)
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                android.util.Log.e("SancharSaathiAnalysis", "BACKEND_ANALYSIS_FAILED: status=${response.code()}, error=$errBody")
                NetworkResult.Failure(
                    reason = FailureReason.SERVER_ERROR,
                    message = "Server returned error status ${response.code()}: $errBody"
                )
            }
        } catch (e: SocketTimeoutException) {
            android.util.Log.e("SancharSaathiAnalysis", "BACKEND_ANALYSIS_TIMEOUT: ${e.message}. Executing OnDeviceSecurityEngine...")
            val onDeviceResult = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                analysisId = request.messageId,
                text = request.text,
                sender = request.senderId,
                timestamp = request.timestampEpochMillis,
                source = request.source
            ).copy(degraded = true, degradedReason = "offline_on_device_fallback")
            historyStore.add(onDeviceResult, source = request.source)
            NetworkResult.Success(onDeviceResult)
        } catch (e: IOException) {
            android.util.Log.e("SancharSaathiAnalysis", "BACKEND_ANALYSIS_NO_CONNECTION: ${e.message}. Executing OnDeviceSecurityEngine...")
            val onDeviceResult = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                analysisId = request.messageId,
                text = request.text,
                sender = request.senderId,
                timestamp = request.timestampEpochMillis,
                source = request.source
            ).copy(degraded = true, degradedReason = "offline_on_device_fallback")
            historyStore.add(onDeviceResult, source = request.source)
            NetworkResult.Success(onDeviceResult)
        } catch (e: Exception) {
            android.util.Log.e("SancharSaathiAnalysis", "BACKEND_ANALYSIS_UNKNOWN_ERROR: ${e.message}. Executing OnDeviceSecurityEngine...")
            val onDeviceResult = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                analysisId = request.messageId,
                text = request.text,
                sender = request.senderId,
                timestamp = request.timestampEpochMillis,
                source = request.source
            ).copy(degraded = true, degradedReason = "offline_on_device_fallback")
            historyStore.add(onDeviceResult, source = request.source)
            NetworkResult.Success(onDeviceResult)
        }
    }

    override fun getCachedAnalysis(analysisId: String): RiskResult? {
        return historyStore.get(analysisId)
    }

    private fun mapDtoToDomain(dto: AnalyzeResponseDto): RiskResult {
        val level = try {
            RiskLevel.valueOf(dto.riskLevel.uppercase())
        } catch (e: Exception) {
            RiskLevel.LOW
        }
        val signals = dto.signals.map { s ->
            RiskSignal(
                category = s.category,
                code = s.code,
                description = s.description,
                technicalDetail = s.technicalDetail,
                weight = s.weight,
                triggered = s.triggered
            )
        }
        return RiskResult(
            analysisId = dto.analysisId,
            riskScore = dto.riskScore,
            riskLevel = level,
            confidence = dto.confidence,
            reasons = dto.reasons,
            signals = signals,
            recommendedAction = dto.recommendedAction,
            shouldBlock = dto.shouldBlock,
            shouldReport = dto.shouldReport,
            detectedUrl = dto.detectedUrl,
            sender = dto.sender,
            modelVersion = dto.modelVersion,
            degraded = dto.degraded,
            degradedReason = dto.degradedReason
        )
    }
}
