package com.sancharsaathi.app.domain.usecase

import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.data.repository.ReportRepository
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.domain.model.ThreatReport

class SubmitReportUseCase(
    private val repository: ReportRepository
) {
    suspend operator fun invoke(result: RiskResult): NetworkResult<ThreatReport> {
        val threatType = when {
            result.detectedUrl != null && result.riskScore >= 70 -> "Phishing Link & Credential Harvest"
            result.riskScore >= 70 -> "Urgency Social Engineering Scam"
            else -> "Suspicious Message Activity"
        }
        return repository.submitReport(
            analysisId = result.analysisId,
            threatType = threatType,
            urlOrDomain = result.detectedUrl ?: result.sender,
            riskScore = result.riskScore,
            riskLevel = result.riskLevel.name,
            evidenceSummary = result.reasons
        )
    }
}
