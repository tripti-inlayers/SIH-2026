package com.sancharsaathi.app.domain.capture

import com.sancharsaathi.app.domain.model.AnalysisRequest
import kotlinx.coroutines.flow.Flow

interface ContentCaptureSource {
    fun observe(): Flow<AnalysisRequest>
}
