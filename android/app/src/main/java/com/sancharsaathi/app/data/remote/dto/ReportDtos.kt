package com.sancharsaathi.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ReportRequestDto(
    @SerializedName("analysis_id") val analysisId: String,
    @SerializedName("threat_type") val threatType: String,
    @SerializedName("url_or_domain") val urlOrDomain: String?,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("evidence_summary") val evidenceSummary: List<String>
)

data class ReportResponseDto(
    @SerializedName("report_id") val reportId: String,
    @SerializedName("timestamp_epoch_millis") val timestampEpochMillis: Long,
    @SerializedName("threat_type") val threatType: String,
    @SerializedName("url_or_domain") val urlOrDomain: String?,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("evidence_summary") val evidenceSummary: List<String>,
    @SerializedName("submitted") val submitted: Boolean,
    @SerializedName("integration_note") val integrationNote: String
)

data class HealthResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("database") val database: String,
    @SerializedName("threat_intel_provider") val threatIntelProvider: String,
    @SerializedName("identity_provider") val identityProvider: String,
    @SerializedName("version") val version: String,
    @SerializedName("ml_service") val mlService: MlServiceStatusDto?
)

data class MlServiceStatusDto(
    @SerializedName("status") val status: String,
    @SerializedName("mock_mode") val mockMode: Boolean?,
    @SerializedName("service") val service: String?,
    @SerializedName("details") val details: String?
)

data class UrlAnalyzeRequestDto(
    @SerializedName("url") val url: String
)

data class UrlAnalyzeResponseDto(
    @SerializedName("url") val url: String,
    @SerializedName("signals") val signals: List<RiskSignalDto>,
    @SerializedName("url_risk_score") val urlRiskScore: Int
)
