import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_submit_and_get_report():
    report_payload = {
        "analysis_id": "test-analysis-123",
        "threat_type": "Phishing Credential Harvest",
        "url_or_domain": "http://secure-bank0findia-verify.xyz/login",
        "risk_score": 87,
        "risk_level": "HIGH",
        "evidence_summary": ["Impersonation", "Lookalike Domain", "Credential Request"]
    }
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/reports", json=report_payload)
        assert res.status_code == 201
        data = res.json()
        report_id = data["report_id"]
        assert report_id.startswith("RPT-")
        assert data["submitted"] is True

        res_get = await client.get(f"/api/v1/reports/{report_id}")
        assert res_get.status_code == 200
        get_data = res_get.json()
        assert get_data["report_id"] == report_id
