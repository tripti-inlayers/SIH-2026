package com.sancharsaathi.app.domain.classifier

import com.sancharsaathi.app.domain.model.RiskLevel

object MessageClassifier {

    fun classify(text: String): ClassificationResult {
        val normalized = TemplateMatcher.normalizeText(text)

        // Iterate through predefined templates in order of definition (priority order)
        for (template in PredefinedTemplates.templates) {
            if (TemplateMatcher.matches(normalized, template)) {
                return ClassificationResult(
                    riskLevel = template.expectedRiskLevel,
                    riskScore = template.expectedRiskScore,
                    reason = template.reason,
                    matchedTemplateId = template.id,
                    triggeredFeatures = template.matchingFeatures,
                    requiresFallback = false
                )
            }
        }

        // Else case: No template matched. Requires fallback analysis to backend.
        return ClassificationResult(
            riskLevel = RiskLevel.LOW,
            riskScore = 0,
            reason = "Unrecognized pattern. Custom analysis fallback required.",
            matchedTemplateId = null,
            triggeredFeatures = emptyList(),
            requiresFallback = true
        )
    }
}
