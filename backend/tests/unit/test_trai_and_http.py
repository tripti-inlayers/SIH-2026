import pytest
from app.services.identity.trai_registry import TraiHeaderRegistryProvider
from app.services.url_analysis import UrlAnalysisService
from app.schemas.common import RiskSignal, RiskLevel
from app.schemas.analyze import AnalyzeRequest, CaptureSource
from app.services.orchestrator import AnalysisOrchestrator

@pytest.mark.asyncio
async def test_trai_header_extraction_and_verification():
    provider = TraiHeaderRegistryProvider()
    
    # 1. Registered Header: JM-HDFCBK-S
    signals_hdfc, info_hdfc = await provider.verify("JM-HDFCBK-S", "HDFC Bank", [])
    assert info_hdfc.verified is True
    assert info_hdfc.entity_name == "HDFC Bank Limited"
    assert info_hdfc.normalized_header == "HDFCBK"
    assert any(s.code == "TRAI_HEADER_VERIFIED" for s in signals_hdfc)

    # 2. Registered Header: JD-IPAYTM
    signals_paytm, info_paytm = await provider.verify("JD-IPAYTM", "Paytm", [])
    assert info_paytm.verified is True
    assert "Paytm" in info_paytm.brand_name
    assert info_paytm.normalized_header == "IPAYTM"

    # 3. Unknown Alphanumeric Header: AB-UNKNOWN123
    signals_unk, info_unk = await provider.verify("AB-UNKNOWN123", None, [])
    assert info_unk.verified is False
    assert any(s.code == "TRAI_HEADER_NOT_FOUND" for s in signals_unk)
    not_found_signal = next(s for s in signals_unk if s.code == "TRAI_HEADER_NOT_FOUND")
    assert not_found_signal.weight <= 0.05

    # 4. Lookalike / Mismatch Header: AB-SBIKYC claiming State Bank of India
    signals_mis, info_mis = await provider.verify("AB-SBIKYC", "State Bank of India", [])
    assert info_mis.lookalike_warning is True
    assert any(s.code == "TRAI_HEADER_IDENTITY_MISMATCH" for s in signals_mis)
    mis_signal = next(s for s in signals_mis if s.code == "TRAI_HEADER_IDENTITY_MISMATCH")
    assert mis_signal.weight == 0.15


def test_http_legitimate_domain_url_analysis():
    service = UrlAnalysisService()
    
    # http://trai.gov.in
    signals = service.analyze("http://trai.gov.in")
    
    # Should trigger NON_HTTPS signal
    non_https = next((s for s in signals if s.code == "NON_HTTPS"), None)
    assert non_https is not None
    assert non_https.triggered is True
    assert non_https.weight <= 0.08
    
    # Should NOT trigger INVALID_URL or IP_ADDRESS_HOST or SUSPICIOUS_TLD
    assert not any(s.code == "IP_ADDRESS_HOST" and s.triggered for s in signals)
    assert not any(s.code == "SUSPICIOUS_TLD" and s.triggered for s in signals)


@pytest.mark.asyncio
async def test_regression_cases_http_vs_phishing():
    orchestrator = AnalysisOrchestrator()
    
    # TEST A — LEGITIMATE HTTP (http://trai.gov.in)
    req_a = AnalyzeRequest(
        message_id="TEST-REG-A",
        text="Please visit the TRAI website for information. http://trai.gov.in",
        urls=["http://trai.gov.in"],
        sender_id="TRAIHD",
        claimed_organization="TRAI",
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.SMS
    )
    res_a = await orchestrator.analyze(req_a)
    assert res_a.risk_level == RiskLevel.LOW
    assert res_a.should_block is False

    # TEST B — SUSPICIOUS HTTP DOMAIN (http://amazn-login-verify.com)
    req_b = AnalyzeRequest(
        message_id="TEST-REG-B",
        text="Amazon security alert. Verify your account here: http://amazn-login-verify.com",
        urls=["http://amazn-login-verify.com"],
        sender_id="UNKNOWN",
        claimed_organization="Amazon",
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.SMS
    )
    res_b = await orchestrator.analyze(req_b)
    assert res_b.risk_level in (RiskLevel.SUSPICIOUS, RiskLevel.HIGH)
    assert res_b.risk_score >= 60

    # TEST C — GOOGLE IMPERSONATION (http://g-security-auth.xyz)
    req_c = AnalyzeRequest(
        message_id="TEST-REG-C",
        text="Google: Google account security alert. Click here to verify http://g-security-auth.xyz",
        urls=["http://g-security-auth.xyz"],
        sender_id="9876543210",
        claimed_organization="Google",
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.SMS
    )
    res_c = await orchestrator.analyze(req_c)
    assert res_c.risk_level == RiskLevel.HIGH
    assert res_c.should_block is True
    assert res_c.risk_score >= 70

    # TEST D — MALICIOUS HTTPS (Confirmed PhishDestroy threat)
    req_d = AnalyzeRequest(
        message_id="TEST-REG-D",
        text="URGENT: Verify your account immediately: https://0000000000000000000000000.findyourjacket.com",
        urls=["https://0000000000000000000000000.findyourjacket.com"],
        sender_id="9876543210",
        claimed_organization="Jacket Store",
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.SMS
    )
    res_d = await orchestrator.analyze(req_d)
    assert res_d.risk_level == RiskLevel.HIGH
    assert res_d.should_block is True
    assert res_d.threat_intel is not None
    assert res_d.threat_intel.threat is True

    # TEST E — BENIGN HTTPS (https://example.com)
    req_e = AnalyzeRequest(
        message_id="TEST-REG-E",
        text="Check our website: https://example.com",
        urls=["https://example.com"],
        sender_id="MANUAL",
        claimed_organization=None,
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.SMS
    )
    res_e = await orchestrator.analyze(req_e)
    assert res_e.risk_level == RiskLevel.LOW
    assert res_e.should_block is False

    # TEST F — MALICIOUS HTTP (Confirmed PhishDestroy threat over HTTP)
    req_f = AnalyzeRequest(
        message_id="TEST-REG-F",
        text="URGENT: Verify details: http://0000000000000000000000000.findyourjacket.com",
        urls=["http://0000000000000000000000000.findyourjacket.com"],
        sender_id="9876543210",
        claimed_organization=None,
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.SMS
    )
    res_f = await orchestrator.analyze(req_f)
    assert res_f.risk_level == RiskLevel.HIGH
    assert res_f.should_block is True
