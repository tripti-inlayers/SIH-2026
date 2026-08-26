package com.sancharsaathi.app.domain.model

data class ThreatReport(
    val reportId: String,
    val timestampEpochMillis: Long,
    val threatType: String,
    val urlOrDomain: String?,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val evidenceSummary: List<String>,
    val submitted: Boolean,
    val integrationNote: String = "Proposed Reporting Integration — demonstration only."
)
