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
            val response = apiService.analyze(dtoRequest)
            if (response.isSuccessful && response.body() != null) {
                val result = mapDtoToDomain(response.body()!!)
                historyStore.add(result)
                NetworkResult.Success(result)
            } else {
                NetworkResult.Failure(
                    reason = FailureReason.SERVER_ERROR,
                    message = "Server returned error status ${response.code()}"
                )
            }
        } catch (e: SocketTimeoutException) {
            NetworkResult.Failure(
                reason = FailureReason.TIMEOUT,
                message = "Connection timed out while analyzing message."
            )
        } catch (e: IOException) {
            NetworkResult.Failure(
                reason = FailureReason.NO_CONNECTION,
                message = "Full security analysis is currently unavailable."
            )
        } catch (e: Exception) {
            NetworkResult.Failure(
                reason = FailureReason.UNKNOWN,
                message = e.message ?: "An unexpected network error occurred."
            )
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
