package com.sancharsaathi.app.domain.classifier

import com.sancharsaathi.app.domain.model.RiskLevel
import org.junit.Assert.*
import org.junit.Test

class MessageClassifierTest {

    @Test
    fun testPhonePeLoginOtp_Success() {
        val msg = "Your Phonepe login OTP is 987654"
        val result = MessageClassifier.classify(msg)
        assertFalse(result.requiresFallback)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(10, result.riskScore)
        assertEquals("PHONEPE_LOGIN_OTP_01", result.matchedTemplateId)
    }

    @Test
    fun testPhonePeLoginOtp_CaseAndWhitespaceInsensitive() {
        val msg = "  your   phonepe   login   otp   is   112233  "
        val result = MessageClassifier.classify(msg)
        assertFalse(result.requiresFallback)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals("PHONEPE_LOGIN_OTP_01", result.matchedTemplateId)
    }

    @Test
    fun testTransactionOtp_HDFC_Success() {
        val msg1 = "123456 is your OTP for transaction of Rs 500 on HDFC Bank."
        val result1 = MessageClassifier.classify(msg1)
        assertFalse(result1.requiresFallback)
        assertEquals(RiskLevel.LOW, result1.riskLevel)
        assertEquals("HDFC_TRANSACTION_OTP_05", result1.matchedTemplateId)

        val msg2 = "998877 is your OTP for transaction of Rs 45000 on HDFC Bank."
        val result2 = MessageClassifier.classify(msg2)
        assertFalse(result2.requiresFallback)
        assertEquals(RiskLevel.LOW, result2.riskLevel)
    }

    @Test
    fun testAccountSuspensionScam_Success() {
        val msg = "URGENT: Your account has been suspended. Click here to verify your details http://malicious-login-update.com/signin"
        val result = MessageClassifier.classify(msg)
        assertFalse(result.requiresFallback)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(95, result.riskScore)
        assertEquals("ACCOUNT_SUSPENSION_SCAM_25", result.matchedTemplateId)
    }

    @Test
    fun testKycSuspensionScam_Success() {
        val msg = "URGENT: Verify your PhonePe KYC immediately to avoid suspension http://fake-phonepe-kyc.com"
        val result = MessageClassifier.classify(msg)
        assertFalse(result.requiresFallback)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(98, result.riskScore)
        assertEquals("KYC_SUSPENSION_SCAM_26", result.matchedTemplateId)
    }

    @Test
    fun testLegitimateContrast_DoesNotMatchScam() {
        val msg = "Your monthly account statement is available. Please login to your netbanking app to check details."
        val result = MessageClassifier.classify(msg)
        // Should not match any spam templates and fallback
        assertTrue(result.requiresFallback)
        assertNull(result.matchedTemplateId)
    }

    @Test
    fun testUnknownCustomMessage_TriggersFallback() {
        val msg = "Hey friend, are we still meeting for lunch at 1 PM today?"
        val result = MessageClassifier.classify(msg)
        assertTrue(result.requiresFallback)
        assertNull(result.matchedTemplateId)
    }

    @Test
    fun testElectricityBillLegitimate_Success() {
        val msg = "Your electricity bill of Rs 850 is due on 12th Aug. https://bescom.co.in/pay"
        val result = MessageClassifier.classify(msg)
        assertFalse(result.requiresFallback)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals("ELECTRICITY_BILL_OK_23", result.matchedTemplateId)
    }

    @Test
    fun testExpiringRewardPointsScam_Success() {
        val msg = "Dear customer, you have 1000 reward points expiring today. http://redeem-points-now.xyz"
        val result = MessageClassifier.classify(msg)
        assertFalse(result.requiresFallback)
        assertEquals(RiskLevel.SUSPICIOUS, result.riskLevel)
        assertEquals(68, result.riskScore)
        assertEquals("REWARD_POINTS_EXPIRY_32", result.matchedTemplateId)
    }
}
