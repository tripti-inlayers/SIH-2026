package com.sancharsaathi.app.domain.usecase

import com.sancharsaathi.app.data.remote.NetworkResult
import com.sancharsaathi.app.data.repository.AnalysisRepository
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.RiskResult

class AnalyzeContentUseCase(
    private val repository: AnalysisRepository
) {
    suspend operator fun invoke(request: AnalysisRequest): NetworkResult<RiskResult> {
        return repository.analyzeContent(request)
    }
}
