import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_demo_scenario_1_low_risk():
    payload = {
        "message_id": "DEMO-LOW-001",
        "text": "Hi, your order has been shipped and will arrive by Friday. Track here: https://www.indiapost.gov.in/track/12345",
        "urls": ["https://www.indiapost.gov.in/track/12345"],
        "sender_id": "AX-INDPOST",
        "claimed_organization": "India Post",
        "timestamp_epoch_millis": 1700000000000,
        "source": "DEMO"
    }
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/analyze", json=payload)
        assert res.status_code == 200
        data = res.json()
        assert data["risk_level"] == "LOW"
        assert data["should_block"] is False
        assert data["should_report"] is False

@pytest.mark.asyncio
async def test_demo_scenario_2_suspicious():
    payload = {
        "message_id": "DEMO-SUSP-002",
        "text": "Your package could not be delivered. Please confirm your address within 24 hours: http://track-parcel-update.tk/confirm",
        "urls": ["http://track-parcel-update.tk/confirm"],
        "sender_id": "9876543210",
        "claimed_organization": "Courier Service",
        "timestamp_epoch_millis": 1700000000000,
        "source": "DEMO"
    }
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/analyze", json=payload)
        assert res.status_code == 200
        data = res.json()
        assert data["risk_level"] == "SUSPICIOUS"
        assert data["should_block"] is False
        assert data["should_report"] is False

@pytest.mark.asyncio
async def test_demo_scenario_3_high_risk():
    payload = {
        "message_id": "DEMO-HIGH-003",
        "text": "URGENT: Your bank account will be suspended. Verify your PIN immediately to avoid blocking: http://secure-bank0findia-verify.xyz/login",
        "urls": ["http://secure-bank0findia-verify.xyz/login"],
        "sender_id": "9876543210",
        "claimed_organization": "State Bank",
        "timestamp_epoch_millis": 1700000000000,
        "source": "DEMO"
    }
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.post("/api/v1/analyze", json=payload)
        assert res.status_code == 200
        data = res.json()
        assert data["risk_level"] == "HIGH"
        assert data["should_block"] is True
        assert data["should_report"] is True
        assert len(data["reasons"]) >= 1
