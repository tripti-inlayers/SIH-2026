package com.sancharsaathi.app.domain.model

data class AnalysisRequest(
    val messageId: String,
    val text: String,
    val urls: List<String>,
    val senderId: String?,
    val claimedOrganization: String?,
    val language: String?,
    val timestampEpochMillis: Long,
    val source: CaptureSource
)
