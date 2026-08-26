package com.sancharsaathi.app.data.local

import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.CaptureSource

object DemoScenarioProvider {

    fun scenario1LowRisk(): AnalysisRequest = AnalysisRequest(
        messageId = "DEMO-LOW-001",
        text = "Hi, your order has been shipped and will arrive by Friday. Track here: https://www.indiapost.gov.in/track/12345",
        urls = listOf("https://www.indiapost.gov.in/track/12345"),
        senderId = "AX-INDPOST",
        claimedOrganization = "India Post",
        language = "en",
        timestampEpochMillis = System.currentTimeMillis(),
        source = CaptureSource.DEMO
    )

    fun scenario2Suspicious(): AnalysisRequest = AnalysisRequest(
        messageId = "DEMO-SUSP-002",
        text = "Your package could not be delivered. Please confirm your address within 24 hours: http://track-parcel-update.tk/confirm",
        urls = listOf("http://track-parcel-update.tk/confirm"),
        senderId = "9876543210",
        claimedOrganization = "Courier Service",
        language = "en",
        timestampEpochMillis = System.currentTimeMillis(),
        source = CaptureSource.DEMO
    )

    fun scenario3HighRisk(): AnalysisRequest = AnalysisRequest(
        messageId = "DEMO-HIGH-003",
        text = "URGENT: Your bank account will be suspended. Verify your PIN immediately to avoid blocking: http://secure-bank0findia-verify.xyz/login",
        urls = listOf("http://secure-bank0findia-verify.xyz/login"),
        senderId = "9876543210",
        claimedOrganization = "State Bank",
        language = "en",
        timestampEpochMillis = System.currentTimeMillis(),
        source = CaptureSource.DEMO
    )
}
