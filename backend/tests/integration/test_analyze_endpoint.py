import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_health_endpoint():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "ok"

@pytest.mark.asyncio
async def test_analyze_endpoint_success():
    payload = {
        "message_id": "MSG-001",
        "text": "Hi, your package has shipped. Track at https://www.indiapost.gov.in/track/123",
        "urls": ["https://www.indiapost.gov.in/track/123"],
        "sender_id": "AX-INDPOST",
        "claimed_organization": "India Post",
        "timestamp_epoch_millis": 1700000000000,
        "source": "DEMO"
    }
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.post("/api/v1/analyze", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert "risk_level" in data
        assert "risk_score" in data
        assert data["risk_level"] == "LOW"
