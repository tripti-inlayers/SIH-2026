package com.sancharsaathi.app.data.repository

import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.domain.model.ThreatReport

interface ReportRepository {
    suspend fun submitReport(
        analysisId: String,
        threatType: String,
        urlOrDomain: String?,
        riskScore: Int,
        riskLevel: String,
        evidenceSummary: List<String>
    ): NetworkResult<ThreatReport>
}
