package com.sancharsaathi.app.domain.classifier

import com.sancharsaathi.app.domain.model.RiskLevel

data class ClassificationResult(
    val riskLevel: RiskLevel,
    val riskScore: Int,
    val reason: String,
    val matchedTemplateId: String?,
    val triggeredFeatures: List<String>,
    val requiresFallback: Boolean
)
