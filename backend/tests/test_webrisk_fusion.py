import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_webrisk_materially_increases_risk_score():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        clean_res = await client.post("/api/v1/analyze/diagnostics", json={
            "text": "Hello, please find the meeting agenda at https://clean-domain.example.com",
            "urls": ["https://clean-domain.example.com"],
            "sender_id": "+919876543210",
            "mock_webrisk_verdict": "CLEAN"
        })
        assert clean_res.status_code == 200
        clean_data = clean_res.json()
        assert clean_data["webrisk_contribution_points"] == 0
        score_clean = clean_data["final_fused_score"]

        threat_res = await client.post("/api/v1/analyze/diagnostics", json={
            "text": "Hello, please find the meeting agenda at https://clean-domain.example.com",
            "urls": ["https://clean-domain.example.com"],
            "sender_id": "+919876543210",
            "mock_webrisk_verdict": "SOCIAL_ENGINEERING"
        })
        assert threat_res.status_code == 200
        threat_data = threat_res.json()
        
        assert threat_data["webrisk_contribution_points"] == 80
        assert "SOCIAL_ENGINEERING" in threat_data["webrisk_matched_threat_types"]
        assert threat_data["final_fused_score"] >= 80
        assert threat_data["final_risk_level"] == "HIGH"
        assert threat_data["final_fused_score"] > score_clean
        assert (threat_data["final_fused_score"] - score_clean) >= 70

@pytest.mark.asyncio
async def test_clean_webrisk_does_not_suppress_phishing_model():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        res = await client.post("/api/v1/analyze/diagnostics", json={
            "text": "URGENT: Your State Bank account is blocked. Update PAN card now or account will be suspended: https://sbi-kyc-update.com",
            "urls": ["https://sbi-kyc-update.com"],
            "sender_id": "+919999999999",
            "mock_webrisk_verdict": "CLEAN"
        })
        assert res.status_code == 200
        data = res.json()
        assert data["webrisk_contribution_points"] == 0
        assert data["final_fused_score"] >= 70
        assert data["final_risk_level"] in ("HIGH", "SUSPICIOUS")

@pytest.mark.asyncio
async def test_webrisk_unavailable_degrades_gracefully():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        res = await client.post("/api/v1/analyze/diagnostics", json={
            "text": "Please check this link: https://random-link.com",
            "urls": ["https://random-link.com"],
            "sender_id": "+919876543210",
            "mock_webrisk_verdict": "UNAVAILABLE"
        })
        assert res.status_code == 200
        data = res.json()
        assert data["webrisk_request_status"] == "UNAVAILABLE"
        assert data["degraded"] is True
        assert "threat_intel_unavailable" in data["degraded_reasons"]
