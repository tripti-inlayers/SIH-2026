import pytest
from unittest.mock import patch, AsyncMock, MagicMock
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
    with patch("app.services.ml_analysis.httpx.AsyncClient") as mock_client_class:
        mock_instance = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_instance
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"prediction": 0, "label": "ham", "confidence": 0.95}
        mock_resp.raise_for_status.return_value = None
        mock_instance.post.return_value = mock_resp

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            resp = await client.post("/api/v1/analyze", json=payload)
            assert resp.status_code == 200
            data = resp.json()
            assert "risk_level" in data
            assert "risk_score" in data
            assert data["risk_level"] == "LOW"

@pytest.mark.asyncio
async def test_analyze_endpoint_with_ml_spam():
    payload = {
        "message_id": "MSG-002",
        "text": "URGENT: Click here http://bad.com",
        "urls": ["http://bad.com"],
        "sender_id": "UNKNOWN",
        "timestamp_epoch_millis": 1700000000000,
        "source": "SMS"
    }
    with patch("app.services.ml_analysis.httpx.AsyncClient") as mock_client_class:
        mock_instance = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_instance
        
        mock_response = MagicMock()
        mock_response.json.return_value = {"prediction": 1, "label": "spam", "confidence": 0.9}
        mock_response.raise_for_status.return_value = None
        mock_response.status_code = 200
        mock_instance.post.return_value = mock_response

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            resp = await client.post("/api/v1/analyze", json=payload)
            assert resp.status_code == 200
            data = resp.json()
            ml_signal = next((s for s in data["signals"] if s["code"] == "AI_SPAM_DETECTED"), None)
            assert ml_signal is not None
            assert ml_signal["triggered"] is True
