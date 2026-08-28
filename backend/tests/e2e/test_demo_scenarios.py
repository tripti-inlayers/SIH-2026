import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from unittest.mock import patch, AsyncMock, MagicMock
from app.services.threat_intel.base import ThreatIntelResult, ThreatIntelVerdict

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
    with patch("app.services.ml_analysis.httpx.AsyncClient") as mock_client_class, \
         patch("app.api.v1.analyze.orchestrator.threat_intel_provider.lookup") as mock_lookup:
        mock_instance = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_instance
        mock_response = MagicMock()
        mock_response.json.return_value = {"prediction": 0, "label": "ham", "confidence": 0.95}
        mock_response.raise_for_status.return_value = None
        mock_response.status_code = 200
        mock_instance.post.return_value = mock_response

        mock_lookup.return_value = ThreatIntelResult(
            provider="phishdestroy",
            checked=True,
            reachable=True,
            threat=False,
            riskScore=0,
            severity=None,
            flags=[],
            matchedKeywords=[],
            error=None,
            degraded=False,
            verdict=ThreatIntelVerdict.CHECKED_CLEAN
        )

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
    with patch("app.services.ml_analysis.httpx.AsyncClient") as mock_client_class, \
         patch("app.api.v1.analyze.orchestrator.threat_intel_provider.lookup") as mock_lookup:
        mock_instance = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_instance
        mock_response = MagicMock()
        # Ham prediction with confidence 0.70 (spam probability = 0.30)
        mock_response.json.return_value = {"prediction": 0, "label": "ham", "confidence": 0.70}
        mock_response.raise_for_status.return_value = None
        mock_response.status_code = 200
        mock_instance.post.return_value = mock_response

        # PhishDestroy flags domain with threat=True but riskScore=65
        # pd_contribution: 65 * 0.6 = 39
        # ml_contribution: 0.30 * 25 = 7.5
        # local rules: urgency (+0.15 weight -> 1.8)
        # heuristics: HTTP + TLD (+0.15 weight -> 0.78)
        # Total = 39 + 7.5 + 1.8 + 0.78 = 49.08 -> 49 (LOW)
        # To get SUSPICIOUS, let's set riskScore=68: 68 * 0.6 = 40.8. Total = 40.8 + 7.5 + 1.8 + 0.78 = 50.88 -> 51 (SUSPICIOUS)
        mock_lookup.return_value = ThreatIntelResult(
            provider="phishdestroy",
            checked=True,
            reachable=True,
            threat=True,
            riskScore=68,
            severity="medium",
            flags=["suspicious_domain"],
            matchedKeywords=["confirm"],
            error=None,
            degraded=False,
            verdict=ThreatIntelVerdict.CHECKED_THREAT
        )

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
    with patch("app.services.ml_analysis.httpx.AsyncClient") as mock_client_class, \
         patch("app.api.v1.analyze.orchestrator.threat_intel_provider.lookup") as mock_lookup:
        mock_instance = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_instance
        mock_response = MagicMock()
        # Spam prediction with confidence 0.90
        mock_response.json.return_value = {"prediction": 1, "label": "spam", "confidence": 0.90}
        mock_response.raise_for_status.return_value = None
        mock_response.status_code = 200
        mock_instance.post.return_value = mock_response

        # PhishDestroy returns threat=True and riskScore=85
        # Triggers Threat Floor of 80 (HIGH)
        mock_lookup.return_value = ThreatIntelResult(
            provider="phishdestroy",
            checked=True,
            reachable=True,
            threat=True,
            riskScore=85,
            severity="high",
            flags=["phishing_domain"],
            matchedKeywords=["bank"],
            error=None,
            degraded=False,
            verdict=ThreatIntelVerdict.CHECKED_THREAT
        )

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            res = await client.post("/api/v1/analyze", json=payload)
            assert res.status_code == 200
            data = res.json()
            assert data["risk_level"] == "HIGH"
            assert data["should_block"] is True
            assert data["should_report"] is True
            assert len(data["reasons"]) >= 1
