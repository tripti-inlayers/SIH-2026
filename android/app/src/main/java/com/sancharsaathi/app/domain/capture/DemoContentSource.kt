package com.sancharsaathi.app.domain.capture

import com.sancharsaathi.app.data.local.DemoScenarioProvider
import com.sancharsaathi.app.domain.model.AnalysisRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DemoContentSource : ContentCaptureSource {
    private val _demoFlow = MutableSharedFlow<AnalysisRequest>(extraBufferCapacity = 1)

    fun triggerScenario(scenarioIndex: Int): AnalysisRequest {
        val request = when (scenarioIndex) {
            1 -> DemoScenarioProvider.scenario1LowRisk()
            2 -> DemoScenarioProvider.scenario2Suspicious()
            else -> DemoScenarioProvider.scenario3HighRisk()
        }
        _demoFlow.tryEmit(request)
        return request
    }

    override fun observe(): Flow<AnalysisRequest> = _demoFlow.asSharedFlow()
}
