package com.sancharsaathi.app.domain.classifier

object TemplateMatcher {

    fun normalizeText(text: String): String {
        return text.trim()
            .replace(Regex("""\s+"""), " ") // Normalize multiple whitespaces
    }

    fun matches(normalizedText: String, template: MessageTemplate): Boolean {
        // Standard matching on the compiled regex pattern
        return template.pattern.matches(normalizedText)
    }
}
