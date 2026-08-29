package com.sancharsaathi.app.domain.engine

import com.sancharsaathi.app.domain.classifier.MessageClassifier
import com.sancharsaathi.app.domain.model.CaptureSource
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.domain.model.RiskSignal
import java.net.URI
import java.util.Locale

object OnDeviceSecurityEngine {

    private val URGENCY_PATTERNS = listOf(
        Regex("""(?i)\b(urgent|immediately|immediate|within \d+ (hours?|mins?|minutes?)|account (blocked|suspended|deactivated|closed)|action required|expires? (today|soon)|last chance|final notice|emergency)\b"""),
        Regex("""(?i)\b(turant|abhee|karein|band ho jayega|deactivate ho|block ho)\b""")
    )

    private val CREDENTIAL_PATTERNS = listOf(
        Regex("""(?i)\b(otp|one time password|pin|password|cvv|card number|debit card|pan card|aadhaar|kyc details|net banking credentials)\b"""),
        Regex("""(?i)\b(share your otp|enter otp|verify (kyc|pan|aadhaar|pin))\b""")
    )

    private val FINANCIAL_PATTERNS = listOf(
        Regex("""(?i)\b(electricity bill|power cut|disconnected tonight|loan approved|pre-approved|disbursed|cash prize|lottery|refund credited|bonus credited|cryptocurrency|earning guaranteed)\b"""),
        Regex("""(?i)\b(bijli bill|light kaat|paisa mila|lottery lagi)\b""")
    )

    private val REWARD_PATTERNS = listOf(
        Regex("""(?i)\b(congratulations|you have won|winner|lucky draw|claim reward|cashback of rs|gift voucher|free gift|spin and win)\b"""),
        Regex("""(?i)\b(badhaai|inaam|jeeta hai)\b""")
    )

    private val THREAT_PATTERNS = listOf(
        Regex("""(?i)\b(legal notice|court warrant|police case|sim block|sim deactivation|trai notice|arrest warrant|fir registered|challan due)\b"""),
        Regex("""(?i)\b(police|jail|kanooni karwai|trai)\b""")
    )

    private val SHORTENER_DOMAINS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "goo.gl", "ow.ly", "rebrand.ly", "shorte.st", "v.gd"
    )

    private val SUSPICIOUS_TLDS = setOf(
        "xyz", "top", "tk", "ml", "ga", "cf", "gq", "icu", "buzz", "club", "work", "fit", "cn", "ru", "live", "vip", "monster", "rest", "bar", "shop"
    )

    private val RECOGNIZED_ORGS = listOf(
        "State Bank" to listOf("sbi", "state bank", "sbin"),
        "HDFC Bank" to listOf("hdfc", "hdfcbk"),
        "ICICI Bank" to listOf("icici", "icicib"),
        "Axis Bank" to listOf("axis", "axisbk"),
        "Punjab National Bank" to listOf("pnb", "punjab national"),
        "India Post" to listOf("indiapost", "post office", "dak seva"),
        "Income Tax" to listOf("income tax", "it department", "incometax"),
        "Electricity Board" to listOf("electricity", "power dept", "bses", "tneb", "uppcl", "mseb", "dhbvn"),
        "Paytm" to listOf("paytm", "one97"),
        "PhonePe" to listOf("phonepe"),
        "Google" to listOf("google", "gpay"),
        "Amazon" to listOf("amazon", "amzn"),
        "Flipkart" to listOf("flipkart"),
        "IRCTC" to listOf("irctc", "railway"),
        "TRAI" to listOf("trai", "telecom department", "dot")
    )

    /**
     * Performs a complete on-device security evaluation of any message or URL.
     * Guaranteed deterministic, zero-latency, 100% offline.
     */
    fun analyze(
        analysisId: String,
        text: String,
        sender: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        source: CaptureSource = CaptureSource.SMS
    ): RiskResult {
        val signals = mutableListOf<RiskSignal>()
        val urls = extractUrls(text)
        val primaryUrl = urls.firstOrNull()

        // 1. Check local template database first (e.g. standard HAM OTPs vs known SPAM rules)
        val templateMatch = MessageClassifier.classify(text)
        if (!templateMatch.requiresFallback && templateMatch.matchedTemplateId != null) {
            val templateSignal = RiskSignal(
                category = "local_template",
                code = templateMatch.matchedTemplateId,
                description = templateMatch.reason,
                technicalDetail = "Matched certified template rules",
                weight = templateMatch.riskScore / 100.0,
                triggered = templateMatch.riskScore > 40
            )
            signals.add(templateSignal)
            
            // If it's a verified safe low-risk template (e.g. standard Google/Amazon/Bank OTP), return fast
            if (templateMatch.riskLevel == RiskLevel.LOW && urls.isEmpty()) {
                return RiskResult(
                    analysisId = analysisId,
                    riskScore = templateMatch.riskScore,
                    riskLevel = RiskLevel.LOW,
                    confidence = 0.95,
                    reasons = listOf(templateMatch.reason),
                    signals = signals,
                    recommendedAction = "Routine verification notice. Safe to interact.",
                    shouldBlock = false,
                    shouldReport = false,
                    detectedUrl = null,
                    sender = sender,
                    modelVersion = "on-device-1.0.0",
                    degraded = false,
                    smsBody = text,
                    timestamp = timestamp
                )
            }
        }

        // 2. Organization Claim & DLT Header Checks
        val detectedOrg = detectClaimedOrg(text)
        if (detectedOrg != null) {
            signals.add(RiskSignal(
                category = "message",
                code = "IMPERSONATION_CLAIM",
                description = "Message claims to represent $detectedOrg.",
                technicalDetail = "Claimed organization: $detectedOrg",
                weight = 0.05,
                triggered = true
            ))

            // Check if sender is a personal mobile number pretending to be a bank/institution
            if (sender != null) {
                val isPersonalNumber = sender.matches(Regex("""^(\+91|91|0)?[6-9]\d{9}$"""))
                val isDltHeader = sender.matches(Regex("""^[A-Za-z]{2}-[A-Za-z0-9]{6}$""")) || sender.matches(Regex("""^[A-Za-z0-9]{6,9}$"""))

                if (isPersonalNumber) {
                    signals.add(RiskSignal(
                        category = "identity",
                        code = "SENDER_ORGANIZATION_MISMATCH",
                        description = "Sender '$sender' is a personal mobile number impersonating $detectedOrg.",
                        technicalDetail = "Institutions use registered DLT sender IDs, not personal 10-digit SIM numbers.",
                        weight = 0.35,
                        triggered = true
                    ))
                } else if (!isDltHeader) {
                    signals.add(RiskSignal(
                        category = "identity",
                        code = "DLT_HEADER_UNVERIFIED",
                        description = "Sender '$sender' does not match official TRAI DLT registration standards.",
                        technicalDetail = "Unverified sender format",
                        weight = 0.15,
                        triggered = true
                    ))
                }
            }
        }

        // 3. Message NLP & Phishing Indicators
        if (URGENCY_PATTERNS.any { it.containsMatchIn(text) }) {
            signals.add(RiskSignal(
                category = "message",
                code = "URGENCY_LANGUAGE",
                description = "Message creates artificial urgency or panic to force immediate action.",
                technicalDetail = "Pattern matched high-pressure psychological triggers",
                weight = 0.20,
                triggered = true
            ))
        }

        if (CREDENTIAL_PATTERNS.any { it.containsMatchIn(text) }) {
            signals.add(RiskSignal(
                category = "message",
                code = "CREDENTIAL_REQUEST",
                description = "Message requests sensitive verification details, OTP, or KYC updates.",
                technicalDetail = "Pattern matched credential harvesting request",
                weight = 0.30,
                triggered = true
            ))
        }

        if (FINANCIAL_PATTERNS.any { it.containsMatchIn(text) }) {
            signals.add(RiskSignal(
                category = "message",
                code = "FINANCIAL_REQUEST",
                description = "Message involves suspicious electricity disconnection, loans, or unverified payments.",
                technicalDetail = "Pattern matched high-risk financial/utility claim",
                weight = 0.25,
                triggered = true
            ))
        }

        if (REWARD_PATTERNS.any { it.containsMatchIn(text) }) {
            signals.add(RiskSignal(
                category = "message",
                code = "REWARD_BAIT",
                description = "Message offers unrealistic cash prize, lottery, or reward voucher.",
                technicalDetail = "Pattern matched advance-fee/lottery scam bait",
                weight = 0.25,
                triggered = true
            ))
        }

        if (THREAT_PATTERNS.any { it.containsMatchIn(text) }) {
            signals.add(RiskSignal(
                category = "message",
                code = "THREAT_LANGUAGE",
                description = "Message uses intimidation, law enforcement, or SIM suspension threats.",
                technicalDetail = "Pattern matched coercive intimidation markers",
                weight = 0.30,
                triggered = true
            ))
        }

        // 4. URL Deep Structural Analysis
        if (primaryUrl != null) {
            val urlSignals = analyzeUrlOnDevice(primaryUrl, detectedOrg)
            signals.addAll(urlSignals)
        }

        // 5. Calculate Final Risk Score & Fusion
        var rawScore = 0.0
        signals.filter { it.triggered }.forEach { s ->
            rawScore += s.weight * 100.0
        }

        // Apply heuristic multipliers
        val hasPhishingUrl = signals.any { it.triggered && it.category == "url" && it.weight >= 0.20 }
        val hasImpersonation = signals.any { it.triggered && it.code == "SENDER_ORGANIZATION_MISMATCH" }
        val hasCredential = signals.any { it.triggered && it.code == "CREDENTIAL_REQUEST" }

        if (hasPhishingUrl && (hasImpersonation || hasCredential)) {
            rawScore = rawScore.coerceAtLeast(85.0)
        } else if (hasPhishingUrl) {
            rawScore = rawScore.coerceAtLeast(70.0)
        } else if (hasImpersonation) {
            rawScore = rawScore.coerceAtLeast(65.0)
        }

        val finalScore = rawScore.toInt().coerceIn(0, 100)
        val level = when {
            finalScore >= 70 -> RiskLevel.HIGH
            finalScore >= 40 -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.LOW
        }

        val reasons = signals.filter { it.triggered }.map { it.description }
        val finalReasons = if (reasons.isEmpty()) {
            listOf("No significant phishing or social engineering indicators detected.")
        } else {
            reasons.take(5)
        }

        val recommendedAction = when (level) {
            RiskLevel.HIGH -> "Danger: Phishing threat detected. Do not click links, share OTP, or send money. Report immediately."
            RiskLevel.SUSPICIOUS -> "Caution: Suspicious sender or unverified link. Verify through official channels before proceeding."
            RiskLevel.LOW -> "Looks safe. Always remember official banks never ask for OTP or passwords."
        }

        return RiskResult(
            analysisId = analysisId,
            riskScore = finalScore,
            riskLevel = level,
            confidence = if (signals.any { it.triggered }) 0.90 else 0.85,
            reasons = finalReasons,
            signals = signals,
            recommendedAction = recommendedAction,
            shouldBlock = level == RiskLevel.HIGH,
            shouldReport = level == RiskLevel.HIGH,
            detectedUrl = primaryUrl,
            sender = sender,
            modelVersion = "on-device-1.0.0",
            degraded = false,
            smsBody = text,
            timestamp = timestamp
        )
    }

    private fun analyzeUrlOnDevice(urlStr: String, claimedOrg: String?): List<RiskSignal> {
        val signals = mutableListOf<RiskSignal>()
        try {
            val uri = URI(urlStr)
            val host = uri.host?.lowercase(Locale.getDefault()) ?: ""
            val scheme = uri.scheme?.lowercase(Locale.getDefault()) ?: "http"

            // A. Check for raw IP host (e.g. http://192.168.1.1/login or http://45.33.22.11)
            if (host.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) {
                signals.add(RiskSignal(
                    category = "url",
                    code = "IP_ADDRESS_HOST",
                    description = "Link points directly to an IP address ($host) instead of a domain name.",
                    technicalDetail = "Direct IP links bypass domain reputation and DNS security.",
                    weight = 0.35,
                    triggered = true
                ))
            }

            // B. Insecure HTTP
            if (scheme == "http") {
                signals.add(RiskSignal(
                    category = "url",
                    code = "NON_HTTPS",
                    description = "The link uses insecure unencrypted HTTP.",
                    technicalDetail = "Legitimate institutions strictly use HTTPS.",
                    weight = 0.10,
                    triggered = true
                ))
            }

            // C. URL Shorteners
            if (SHORTENER_DOMAINS.any { host.equals(it, ignoreCase = true) || host.endsWith(".$it") }) {
                signals.add(RiskSignal(
                    category = "url",
                    code = "URL_SHORTENER",
                    description = "The link uses a URL shortener service ($host) to mask its actual destination.",
                    technicalDetail = "Scammers use shorteners to bypass domain filters.",
                    weight = 0.20,
                    triggered = true
                ))
            }

            // D. Suspicious TLD
            val tld = host.substringAfterLast(".", "")
            if (SUSPICIOUS_TLDS.contains(tld)) {
                signals.add(RiskSignal(
                    category = "url",
                    code = "SUSPICIOUS_TLD",
                    description = "The link uses a top-level domain (.$tld) with high historical scam volume.",
                    technicalDetail = "Abused TLD: .$tld",
                    weight = 0.25,
                    triggered = true
                ))
            }

            // E. Lookalike Domain & Hyphen Abuse
            val targetBrands = listOf("amazon", "amazn", "google", "googl", "g-security", "sbi", "hdfc", "icici", "paytm", "kyc", "incometax", "aadhaar", "uidai", "gov", "nic")
            val isLookalike = targetBrands.any { keyword ->
                host.contains(keyword) && !host.endsWith(".gov.in") && !host.endsWith(".nic.in") && !host.equals("$keyword.com") && !host.equals("$keyword.co.in")
            }
            if (isLookalike) {
                signals.add(RiskSignal(
                    category = "url",
                    code = "DOMAIN_LOOKALIKE",
                    description = "The link domain mimics a legitimate institution or service ($host).",
                    technicalDetail = "Lookalike brand keyword detected in unverified domain.",
                    weight = 0.35,
                    triggered = true
                ))
            }

            // F. Security Intent Keywords in Host
            val securityKeywords = listOf("login", "verify", "auth", "security", "account", "update", "signin", "billing")
            if (securityKeywords.any { host.contains(it) }) {
                signals.add(RiskSignal(
                    category = "url",
                    code = "SECURITY_INTENT_KEYWORD",
                    description = "The domain name contains security/login verification keywords.",
                    technicalDetail = "Host '$host' contains sensitive security intent tokens.",
                    weight = 0.20,
                    triggered = true
                ))
            }

            // G. Excessive Hyphens or Subdomains
            if (host.count { it == '-' } >= 2 || host.count { it == '.' } >= 3) {
                signals.add(RiskSignal(
                    category = "url",
                    code = "EXCESSIVE_SUBDOMAINS",
                    description = "The link domain has excessive hyphens or nested subdomains ($host).",
                    technicalDetail = "Domain obfuscation technique.",
                    weight = 0.15,
                    triggered = true
                ))
            }

        } catch (e: Exception) {
            signals.add(RiskSignal(
                category = "url",
                code = "MALFORMED_URL",
                description = "The link structure is malformed or intentionally obfuscated.",
                technicalDetail = "URI parse error: ${e.message}",
                weight = 0.20,
                triggered = true
            ))
        }
        return signals
    }

    private fun extractUrls(text: String): List<String> {
        val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ';', ')') }.toList()
    }

    private fun detectClaimedOrg(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        for ((orgName, keywords) in RECOGNIZED_ORGS) {
            if (keywords.any { lower.contains(it) }) {
                return orgName
            }
        }
        return null
    }
}
