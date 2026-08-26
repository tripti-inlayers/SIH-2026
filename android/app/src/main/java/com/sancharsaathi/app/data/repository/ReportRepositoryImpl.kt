package com.sancharsaathi.app.data.repository

import com.sancharsaathi.app.data.remote.AnalysisApiService
import com.sancharsaathi.app.data.remote.FailureReason
import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.data.remote.dto.ReportRequestDto
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.ThreatReport
import java.io.IOException

class ReportRepositoryImpl(
    private val apiService: AnalysisApiService
) : ReportRepository {

    override suspend fun submitReport(
        analysisId: String,
        threatType: String,
        urlOrDomain: String?,
        riskScore: Int,
        riskLevel: String,
        evidenceSummary: List<String>
    ): NetworkResult<ThreatReport> {
        return try {
            val dto = ReportRequestDto(
                analysisId = analysisId,
                threatType = threatType,
                urlOrDomain = urlOrDomain,
                riskScore = riskScore,
                riskLevel = riskLevel,
                evidenceSummary = evidenceSummary
            )
            val resp = apiService.submitReport(dto)
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!
                val levelEnum = try {
                    RiskLevel.valueOf(body.riskLevel.uppercase())
                } catch (e: Exception) {
                    RiskLevel.HIGH
                }
                val report = ThreatReport(
                    reportId = body.reportId,
                    timestampEpochMillis = body.timestampEpochMillis,
                    threatType = body.threatType,
                    urlOrDomain = body.urlOrDomain,
                    riskScore = body.riskScore,
                    riskLevel = levelEnum,
                    evidenceSummary = body.evidenceSummary,
                    submitted = body.submitted,
                    integrationNote = body.integrationNote
                )
                NetworkResult.Success(report)
            } else {
                NetworkResult.Failure(FailureReason.SERVER_ERROR, "Server returned error ${resp.code()}")
            }
        } catch (e: IOException) {
            NetworkResult.Failure(FailureReason.NO_CONNECTION, "Network connection error while submitting report.")
        } catch (e: Exception) {
            NetworkResult.Failure(FailureReason.UNKNOWN, e.message ?: "Failed to submit report.")
        }
    }
}
