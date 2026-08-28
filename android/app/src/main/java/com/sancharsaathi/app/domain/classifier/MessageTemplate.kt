package com.sancharsaathi.app.domain.classifier

import com.sancharsaathi.app.domain.model.RiskLevel

data class MessageTemplate(
    val id: String,
    val pattern: Regex,
    val expectedRiskLevel: RiskLevel,
    val expectedRiskScore: Int,
    val reason: String,
    val matchingFeatures: List<String>
)

object PredefinedTemplates {
    val templates = listOf(
        // === LOW RISK (HAM) TEMPLATES ===
        MessageTemplate(
            id = "PHONEPE_LOGIN_OTP_01",
            pattern = Regex("""(?i)Your\s+Phonepe\s+login\s+OTP\s+is\s+\d+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Standard login OTP verification.",
            matchingFeatures = listOf("OTP", "PhonePe")
        ),
        MessageTemplate(
            id = "AMAZON_LOGIN_OTP_02",
            pattern = Regex("""(?i)Your\s+Amazon\s+login\s+OTP\s+is\s+\d+\.\s+Do\s+not\s+share\s+this\s+with\s+anyone\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 12,
            reason = "Standard Amazon authentication.",
            matchingFeatures = listOf("OTP", "Amazon")
        ),
        MessageTemplate(
            id = "GOOGLE_VERIFY_CODE_03",
            pattern = Regex("""(?i)Use\s+\d+\s+as\s+your\s+verification\s+code\s+for\s+Google\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 8,
            reason = "Routine Google validation.",
            matchingFeatures = listOf("verification code", "Google")
        ),
        MessageTemplate(
            id = "NETFLIX_VERIFY_CODE_04",
            pattern = Regex("""(?i)Your\s+Netflix\s+verification\s+code\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 15,
            reason = "Routine Netflix authentication code.",
            matchingFeatures = listOf("verification code", "Netflix")
        ),
        MessageTemplate(
            id = "HDFC_TRANSACTION_OTP_05",
            pattern = Regex("""(?i)\d+\s+is\s+your\s+OTP\s+for\s+transaction\s+of\s+Rs\s+\d+\s+on\s+HDFC\s+Bank\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 20,
            reason = "Standard banking transaction alert.",
            matchingFeatures = listOf("OTP", "transaction", "HDFC Bank")
        ),
        MessageTemplate(
            id = "SBI_BANK_OTP_06",
            pattern = Regex("""(?i)Never\s+share\s+your\s+OTP\.\s+Your\s+SBI\s+bank\s+OTP\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 18,
            reason = "Standard SBI transaction verify.",
            matchingFeatures = listOf("OTP", "SBI")
        ),
        MessageTemplate(
            id = "SWIGGY_LOGIN_CODE_07",
            pattern = Regex("""(?i)Your\s+Swiggy\s+login\s+code\s+is\s+\d+\.\s+Valid\s+for\s+\d+\s+minutes\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Swiggy login token validation.",
            matchingFeatures = listOf("login code", "Swiggy")
        ),
        MessageTemplate(
            id = "ZOMATO_LOGIN_OTP_08",
            pattern = Regex("""(?i)Zomato:\s+\d+\s+is\s+your\s+OTP\s+to\s+login\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Zomato verification OTP.",
            matchingFeatures = listOf("OTP", "Zomato")
        ),
        MessageTemplate(
            id = "UBER_LOGIN_CODE_09",
            pattern = Regex("""(?i)Your\s+Uber\s+code\s+is\s+\d+\.\s+Never\s+share\s+this\s+code\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 12,
            reason = "Uber authentication token.",
            matchingFeatures = listOf("Uber", "code")
        ),
        MessageTemplate(
            id = "WHATSAPP_REG_CODE_10",
            pattern = Regex("""(?i)Your\s+WhatsApp\s+registration\s+code\s+is\s+\d+-\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 8,
            reason = "WhatsApp setup verification.",
            matchingFeatures = listOf("registration code", "WhatsApp")
        ),
        MessageTemplate(
            id = "APPLE_ID_VERIFY_11",
            pattern = Regex("""(?i)Apple\s+ID\s+verification\s+code:\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 12,
            reason = "Apple Account access code.",
            matchingFeatures = listOf("verification code", "Apple ID")
        ),
        MessageTemplate(
            id = "MICROSOFT_ACCESS_12",
            pattern = Regex("""(?i)Microsoft\s+access\s+code:\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Microsoft security login.",
            matchingFeatures = listOf("access code", "Microsoft")
        ),
        MessageTemplate(
            id = "INSTAGRAM_SECURITY_13",
            pattern = Regex("""(?i)Your\s+Instagram\s+security\s+code\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 14,
            reason = "Instagram authorization code.",
            matchingFeatures = listOf("security code", "Instagram")
        ),
        MessageTemplate(
            id = "FLIPKART_TRANSACTION_14",
            pattern = Regex("""(?i)OTP\s+for\s+your\s+transaction\s+at\s+Flipkart\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 15,
            reason = "Flipkart order transaction notice.",
            matchingFeatures = listOf("OTP", "Flipkart")
        ),
        MessageTemplate(
            id = "ICICI_BANK_OTP_15",
            pattern = Regex("""(?i)Dear\s+customer,\s+your\s+ICICI\s+bank\s+OTP\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 18,
            reason = "ICICI Bank transaction request.",
            matchingFeatures = listOf("OTP", "ICICI bank")
        ),
        MessageTemplate(
            id = "DISCORD_VERIFY_16",
            pattern = Regex("""(?i)Your\s+verification\s+code\s+for\s+Discord\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Discord account activation.",
            matchingFeatures = listOf("verification code", "Discord")
        ),
        MessageTemplate(
            id = "PAYPAL_SECURITY_17",
            pattern = Regex("""(?i)PayPal:\s+Your\s+security\s+code\s+is\s+\d+\.\s+It\s+expires\s+in\s+\d+\s+minutes\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 15,
            reason = "PayPal login secure check.",
            matchingFeatures = listOf("security code", "PayPal")
        ),
        MessageTemplate(
            id = "TINDER_VERIFY_18",
            pattern = Regex("""(?i)Your\s+Tinder\s+verification\s+code\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Tinder signup validation.",
            matchingFeatures = listOf("verification code", "Tinder")
        ),
        MessageTemplate(
            id = "AIRBNB_VERIFY_19",
            pattern = Regex("""(?i)Your\s+Airbnb\s+verification\s+code\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Airbnb reservation check.",
            matchingFeatures = listOf("verification code", "Airbnb")
        ),
        MessageTemplate(
            id = "PAYTM_LOGIN_OTP_20",
            pattern = Regex("""(?i)OTP\s+to\s+login\s+to\s+your\s+Paytm\s+account\s+is\s+\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 12,
            reason = "Paytm access credentials.",
            matchingFeatures = listOf("OTP", "Paytm")
        ),
        MessageTemplate(
            id = "AMAZON_DELIVERY_OK_21",
            pattern = Regex("""(?i)Your\s+package\s+from\s+Amazon\s+is\s+out\s+for\s+delivery\s+today\.\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 20,
            reason = "Standard package delivery notice with tracking.",
            matchingFeatures = listOf("package", "delivery", "Amazon", "URL")
        ),
        MessageTemplate(
            id = "DOCTOR_REMINDER_OK_22",
            pattern = Regex("""(?i)Reminder:\s+Doctor\s+appointment\s+at\s+\d{1,2}:\d{2}\s*(?:AM|PM)\s+tomorrow\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 5,
            reason = "Doctor appointment calendar notification.",
            matchingFeatures = listOf("appointment", "reminder")
        ),
        MessageTemplate(
            id = "ELECTRICITY_BILL_OK_23",
            pattern = Regex("""(?i)Your\s+electricity\s+bill\s+of\s+Rs\s+\d+\s+is\s+due\s+on\s+\d+(?:st|nd|rd|th)?\s+[A-Za-z]+\.\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 22,
            reason = "Regular electricity utility bill alert.",
            matchingFeatures = listOf("electricity bill", "due", "URL")
        ),
        MessageTemplate(
            id = "BANK_CREDIT_ALERT_24",
            pattern = Regex("""(?i)Salary\s+of\s+Rs\s+\d+\s+credited\s+to\s+your\s+A/c\s+XX\d+\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.LOW,
            expectedRiskScore = 10,
            reason = "Standard transaction credit alert.",
            matchingFeatures = listOf("credited", "A/c")
        ),

        // === HIGH RISK (SPAM) TEMPLATES ===
        MessageTemplate(
            id = "ACCOUNT_SUSPENSION_SCAM_25",
            pattern = Regex("""(?i)URGENT:\s+Your\s+account\s+has\s+been\s+suspended\.\s+Click\s+here\s+to\s+verify\s+your\s+details\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.HIGH,
            expectedRiskScore = 95,
            reason = "Urgent suspension scare combined with malicious validation URL.",
            matchingFeatures = listOf("suspended", "URGENT", "verify", "URL")
        ),
        MessageTemplate(
            id = "KYC_SUSPENSION_SCAM_26",
            pattern = Regex("""(?i)URGENT:\s+Verify\s+your\s+[A-Za-z0-9]+\s+KYC\s+immediately\s+to\s+avoid\s+suspension\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.HIGH,
            expectedRiskScore = 98,
            reason = "Impersonation threat targeting e-wallet KYC details immediately.",
            matchingFeatures = listOf("KYC", "URGENT", "suspension", "URL")
        ),
        MessageTemplate(
            id = "GIFT_CARD_WIN_SCAM_27",
            pattern = Regex("""(?i)Congratulations!\s+You've\s+won\s+a\s+\$\d+(?:,\d+)?\s+[A-Za-z0-9\s]+\s+gift\s+card\.\s+Go\s+to\s+https?://[^\s]+\s+to\s+claim\s+now\.""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.HIGH,
            expectedRiskScore = 90,
            reason = "High-bait cash/gift prize designed to redirect to capture page.",
            matchingFeatures = listOf("Congratulations", "won", "gift card", "URL")
        ),
        MessageTemplate(
            id = "UNUSUAL_LOGIN_SCAM_28",
            pattern = Regex("""(?i)Warning:\s+Unusual\s+login\s+attempt\s+on\s+your\s+account\.\s+Please\s+secure\s+it\s+at\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.HIGH,
            expectedRiskScore = 88,
            reason = "Security alert scare baiting credentials reset on malicious portal.",
            matchingFeatures = listOf("Warning", "Unusual login", "URL")
        ),
        MessageTemplate(
            id = "APPLE_ID_LOCKED_SCAM_29",
            pattern = Regex("""(?i)Your\s+Apple\s+ID\s+has\s+been\s+locked\s+for\s+security\s+reasons\.\s+Verify\s+your\s+identity\s+at\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.HIGH,
            expectedRiskScore = 92,
            reason = "Apple ID credential phishing threat.",
            matchingFeatures = listOf("locked", "Apple ID", "Verify", "URL")
        ),
        MessageTemplate(
            id = "PACKAGE_DELIVERY_FEE_SCAM_30",
            pattern = Regex("""(?i)You\s+have\s+a\s+pending\s+package\s+delivery\.\s+Pay\s+the\s+\$\d+(?:\.\d{2})?\s+fee\s+here\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.HIGH,
            expectedRiskScore = 94,
            reason = "Postal delivery fee bait targeting bank cards.",
            matchingFeatures = listOf("pending package", "delivery", "fee", "URL")
        ),

        // === SUSPICIOUS RISK (SPAM) TEMPLATES ===
        MessageTemplate(
            id = "ACCOUNT_VERIFY_ADDRESS_31",
            pattern = Regex("""(?i)Your\s+account\s+needs\s+verification\.\s+Please\s+update\s+your\s+address\.\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.SUSPICIOUS,
            expectedRiskScore = 65,
            reason = "Request for personal address update using external link.",
            matchingFeatures = listOf("verification", "update", "address", "URL")
        ),
        MessageTemplate(
            id = "REWARD_POINTS_EXPIRY_32",
            pattern = Regex("""(?i)Dear\s+customer,\s+you\s+have\s+\d+\s+reward\s+points\s+expiring\s+today\.\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.SUSPICIOUS,
            expectedRiskScore = 68,
            reason = "Reward urgency expiration targeting unbranded links.",
            matchingFeatures = listOf("customer", "reward points", "expiring", "URL")
        ),
        MessageTemplate(
            id = "CONFIRM_DELIVERY_DETAILS_33",
            pattern = Regex("""(?i)Action\s+required:\s+confirm\s+your\s+delivery\s+details\.\s+https?://[^\s]+""", RegexOption.IGNORE_CASE),
            expectedRiskLevel = RiskLevel.SUSPICIOUS,
            expectedRiskScore = 60,
            reason = "Generic package confirm request without brand info.",
            matchingFeatures = listOf("Action required", "delivery details", "URL")
        )
    )
}
