package com.sancharsaathi.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RiskSignalDto(
    @SerializedName("category") val category: String,
    @SerializedName("code") val code: String,
    @SerializedName("description") val description: String,
    @SerializedName("technical_detail") val technicalDetail: String,
    @SerializedName("weight") val weight: Double,
    @SerializedName("triggered") val triggered: Boolean
)

data class ThreatIntelInfoDto(
    @SerializedName("provider") val provider: String = "phishdestroy",
    @SerializedName("checked") val checked: Boolean = false,
    @SerializedName("reachable") val reachable: Boolean = false,
    @SerializedName("threat") val threat: Boolean = false,
    @SerializedName("risk_score") val riskScore: Int = 0,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("flags") val flags: List<String> = emptyList(),
    @SerializedName("matched_keywords") val matchedKeywords: List<String> = emptyList(),
    @SerializedName("error") val error: String? = null,
    @SerializedName("degraded") val degraded: Boolean = false,
    @SerializedName("verdict") val verdict: String = "UNAVAILABLE"
)

data class AnalyzeResponseDto(
    @SerializedName("analysis_id") val analysisId: String,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("reasons") val reasons: List<String>,
    @SerializedName("signals") val signals: List<RiskSignalDto>,
    @SerializedName("recommended_action") val recommendedAction: String,
    @SerializedName("should_block") val shouldBlock: Boolean,
    @SerializedName("should_report") val shouldReport: Boolean,
    @SerializedName("detected_url") val detectedUrl: String?,
    @SerializedName("sender") val sender: String?,
    @SerializedName("model_version") val modelVersion: String,
    @SerializedName("degraded") val degraded: Boolean = false,
    @SerializedName("degraded_reason") val degradedReason: String? = null,
    @SerializedName("threat_intel") val threatIntel: ThreatIntelInfoDto? = null
)
