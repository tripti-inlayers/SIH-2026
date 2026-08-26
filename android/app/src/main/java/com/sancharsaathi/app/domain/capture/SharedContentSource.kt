package com.sancharsaathi.app.domain.capture

import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.CaptureSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

object SharedContentChannel {
    private val _events = MutableSharedFlow<AnalysisRequest>(extraBufferCapacity = 1)
    val events: Flow<AnalysisRequest> = _events.asSharedFlow()

    fun emitSharedText(sharedText: String): AnalysisRequest {
        val urls = extractUrls(sharedText)
        val request = AnalysisRequest(
            messageId = "SHARED-${UUID.randomUUID().toString().take(8)}",
            text = sharedText,
            urls = urls,
            senderId = "Shared Input",
            claimedOrganization = null,
            language = "en",
            timestampEpochMillis = System.currentTimeMillis(),
            source = CaptureSource.SHARED
        )
        _events.tryEmit(request)
        return request
    }

    private fun extractUrls(text: String): List<String> {
        val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(text).map { it.value }.toList()
    }
}

class SharedContentSource : ContentCaptureSource {
    override fun observe(): Flow<AnalysisRequest> = SharedContentChannel.events
}
