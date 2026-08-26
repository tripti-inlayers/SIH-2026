package com.sancharsaathi.app.data.repository

import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.RiskResult

interface AnalysisRepository {
    suspend fun analyzeContent(request: AnalysisRequest): NetworkResult<RiskResult>
    fun getCachedAnalysis(analysisId: String): RiskResult?
}
