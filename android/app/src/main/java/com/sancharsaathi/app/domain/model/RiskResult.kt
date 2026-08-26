package com.sancharsaathi.app.domain.model

data class RiskResult(
    val analysisId: String,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val confidence: Double,
    val reasons: List<String>,
    val signals: List<RiskSignal>,
    val recommendedAction: String,
    val shouldBlock: Boolean,
    val shouldReport: Boolean,
    val detectedUrl: String?,
    val sender: String?,
    val modelVersion: String,
    val degraded: Boolean = false,
    val degradedReason: String? = null
)
