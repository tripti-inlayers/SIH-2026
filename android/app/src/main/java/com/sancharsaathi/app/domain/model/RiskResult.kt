package com.sancharsaathi.app.domain.model

data class ThreatIntelInfo(
    val provider: String = "phishdestroy",
    val checked: Boolean = false,
    val reachable: Boolean = false,
    val threat: Boolean = false,
    val riskScore: Int = 0,
    val severity: String? = null,
    val flags: List<String> = emptyList(),
    val matchedKeywords: List<String> = emptyList(),
    val error: String? = null,
    val degraded: Boolean = false,
    val verdict: String = "UNAVAILABLE"
)

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
    val degradedReason: String? = null,
    val smsBody: String? = null,
    val timestamp: Long = 0L,
    val threatIntel: ThreatIntelInfo? = null
)
