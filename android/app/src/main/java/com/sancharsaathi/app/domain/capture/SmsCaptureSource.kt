package com.sancharsaathi.app.domain.capture

import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.CaptureSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

object SmsCaptureChannel {
    private val _events = MutableSharedFlow<AnalysisRequest>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<AnalysisRequest> = _events.asSharedFlow()

    fun emitSms(sender: String?, body: String, timestampEpochMillis: Long = System.currentTimeMillis()) {
        val urls = extractUrls(body)
        val request = AnalysisRequest(
            messageId = "SMS-${UUID.randomUUID().toString().take(8)}",
            text = body,
            urls = urls,
            senderId = sender,
            claimedOrganization = detectClaimedOrg(body),
            language = "en",
            timestampEpochMillis = if (timestampEpochMillis > 0) timestampEpochMillis else System.currentTimeMillis(),
            source = CaptureSource.SMS
        )
        _events.tryEmit(request)
    }

    private fun extractUrls(text: String): List<String> {
        val urlRegex = Regex("""(?:https?://|cutt\.ly/|bit\.ly/|tinyurl\.com/|t\.co/|(?:[a-zA-Z0-9-]+\.)+(?:com|ly|in|org|net|xyz|tk|top|io|co|gov|edu)/)[^\s]+""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(text).map { match ->
            val raw = match.value
            if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                "https://$raw"
            } else {
                raw
            }
        }.toList()
    }

    private fun detectClaimedOrg(text: String): String? {
        val lower = text.lowercase()
        return when {
            "sbi" in lower || "state bank" in lower -> "State Bank"
            "indiapost" in lower || "post" in lower -> "India Post"
            "irctc" in lower -> "IRCTC"
            "hdfc" in lower -> "HDFC Bank"
            "courier" in lower || "package" in lower -> "Courier Service"
            else -> null
        }
    }
}

class SmsCaptureSource : ContentCaptureSource {
    override fun observe(): Flow<AnalysisRequest> = SmsCaptureChannel.events
}
