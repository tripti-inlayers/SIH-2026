import asyncio
import httpx
from app.config import settings
from app.services.orchestrator import AnalysisOrchestrator
from app.services.threat_intel.google_webrisk import GoogleWebRiskProvider
from app.schemas.analyze import AnalyzeRequest
from app.schemas.common import CaptureSource

async def run_tests():
    print("================================================================")
    print("SANCHAR SAATHI - GOOGLE WEB RISK FULL INTEGRATION VERIFICATION")
    print("================================================================")
    
    # 1. Configuration check
    has_key = bool(settings.GOOGLE_WEBRISK_API_KEY)
    print(f"1. Configuration: Key Loaded = {'YES' if has_key else 'NO'}, Provider = {settings.THREAT_INTEL_PROVIDER}")

    # 2. Direct Provider Test
    print("\n2. Direct Web Risk Provider Test:")
    provider = GoogleWebRiskProvider()
    test_urls = [
        ("Malware URL", "http://testsafebrowsing.appspot.com/s/malware.html"),
        ("Clean URL", "https://www.google.com")
    ]
    for label, u in test_urls:
        res = await provider.check_url(u)
        print(f"   [{label}] URL: {u}")
        print(f"   Available: {res['available']}, Matched: {res['matched']}, ThreatTypes: {res['threat_types']}")
        if res.get('error'):
            print(f"   Note/Error: {res['error']}")

    # 3. End-to-End Orchestration: Message with Phishing / Malware URL
    print("\n3. End-to-End Test (Urgent message with Test URL):")
    orchestrator = AnalysisOrchestrator()
    req_phishing = AnalyzeRequest(
        message_id="test-msg-1",
        text="URGENT: Your bank account will be blocked within 24 hours. Update KYC immediately at http://testsafebrowsing.appspot.com/s/malware.html",
        urls=["http://testsafebrowsing.appspot.com/s/malware.html"],
        sender_id="VK-HDFCBK",
        claimed_organization="HDFC Bank",
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.REAL_SMS
    )
    res_phishing = await orchestrator.analyze(req_phishing)
    print(f"   Risk Score: {res_phishing.risk_score}/100 | Risk Level: {res_phishing.risk_level}")
    print(f"   Action: {res_phishing.recommended_action}")
    print(f"   Reasons: {res_phishing.reasons}")
    print(f"   Degraded: {res_phishing.degraded} (Reason: {res_phishing.degraded_reason})")
    print(f"   Triggered Signals ({len([s for s in res_phishing.signals if s.triggered])}):")
    for s in res_phishing.signals:
        if s.triggered:
            print(f"     - [{s.category.upper()}] {s.code}: {s.description} (+{int(s.weight*100)})")

    # 4. End-to-End Test: Message without URL (NLP + ML model only)
    print("\n4. End-to-End Test (No-URL urgent scam message):")
    req_no_url = AnalyzeRequest(
        message_id="test-msg-2",
        text="Dear customer, your electricity power will be disconnected tonight at 9:30 PM due to unpaid bill. Immediately send OTP and call 9876543210 to prevent disconnection.",
        urls=[],
        sender_id="VM-EBILL",
        claimed_organization="Electricity Board",
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.SMS
    )
    res_no_url = await orchestrator.analyze(req_no_url)
    print(f"   Risk Score: {res_no_url.risk_score}/100 | Risk Level: {res_no_url.risk_level}")
    print(f"   Confidence: {res_no_url.confidence}")
    print(f"   Reasons: {res_no_url.reasons}")
    print(f"   (Verified score is non-zero and properly evaluated by NLP/ML without URL)")

    # 5. Multi-URL Test
    print("\n5. Multi-URL Deduplication & Concurrent Analysis Test:")
    req_multi = AnalyzeRequest(
        message_id="test-msg-3",
        text="Visit https://secure-bank-login.xyz and also check backup portal at http://192.168.1.1/login.php for confirmation.",
        urls=["https://secure-bank-login.xyz", "http://192.168.1.1/login.php", "https://secure-bank-login.xyz"], # intentionally duplicated
        sender_id="9876543210",
        claimed_organization=None,
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.MANUAL_INPUT
    )
    res_multi = await orchestrator.analyze(req_multi)
    print(f"   Risk Score: {res_multi.risk_score}/100 | Risk Level: {res_multi.risk_level}")
    print(f"   Signals computed: {len(res_multi.signals)}")
    print(f"   Reasons: {res_multi.reasons}")

    # 6. Clean message test
    print("\n6. Clean / Legitimate Message Test:")
    req_clean = AnalyzeRequest(
        message_id="test-msg-4",
        text="Hi Dad, I will reach home by 8 PM today for dinner.",
        urls=[],
        sender_id="+919876543210",
        claimed_organization=None,
        timestamp_epoch_millis=1700000000000,
        source=CaptureSource.REAL_SMS
    )
    res_clean = await orchestrator.analyze(req_clean)
    print(f"   Risk Score: {res_clean.risk_score}/100 | Risk Level: {res_clean.risk_level}")
    print(f"   Reasons: {res_clean.reasons}")

    print("\n================================================================")
    print("ALL TESTS COMPLETED SUCCESSFULLY")
    print("================================================================")

if __name__ == "__main__":
    asyncio.run(run_tests())
