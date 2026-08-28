import pytest
from unittest.mock import patch, AsyncMock, MagicMock
from app.services.threat_intel.google_safebrowsing import GoogleSafeBrowsingProvider
from app.services.threat_intel.multi_provider import MultiThreatIntelProvider
from app.services.threat_intel.base import ThreatIntelVerdict

@pytest.mark.asyncio
async def test_safebrowsing_missing_api_key():
    provider = GoogleSafeBrowsingProvider(api_key=None)
    res = await provider.lookup("https://example.com")
    assert res.verdict == ThreatIntelVerdict.UNKNOWN
    assert "missing" in res.detail

@pytest.mark.asyncio
async def test_safebrowsing_no_match():
    provider = GoogleSafeBrowsingProvider(api_key="test_key")
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {}  # Safe Browsing returns empty dict when clean
        mock_post.return_value = mock_resp

        res = await provider.lookup("https://www.google.com")
        assert res.verdict == ThreatIntelVerdict.UNKNOWN
        assert "no threat match" in res.detail

@pytest.mark.asyncio
async def test_safebrowsing_malicious_match():
    provider = GoogleSafeBrowsingProvider(api_key="test_key")
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {
            "matches": [
                {
                    "threatType": "SOCIAL_ENGINEERING",
                    "platformType": "ANY_PLATFORM",
                    "threatEntryType": "URL",
                    "threat": {"url": "http://testsafebrowsing.appspot.com/s/phishing.html"}
                }
            ]
        }
        mock_post.return_value = mock_resp

        res = await provider.lookup("http://testsafebrowsing.appspot.com/s/phishing.html")
        assert res.verdict == ThreatIntelVerdict.KNOWN_MALICIOUS
        assert "SOCIAL_ENGINEERING" in res.detail

@pytest.mark.asyncio
async def test_safebrowsing_http_error():
    provider = GoogleSafeBrowsingProvider(api_key="invalid_key")
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_resp = MagicMock()
        mock_resp.status_code = 400
        mock_post.return_value = mock_resp

        res = await provider.lookup("https://example.com")
        assert res.verdict == ThreatIntelVerdict.UNKNOWN
        assert "HTTP 400" in res.detail

@pytest.mark.asyncio
async def test_multi_threat_intel_mock_safe():
    provider = MultiThreatIntelProvider()
    res = await provider.lookup("https://www.indiapost.gov.in")
    assert res.verdict == ThreatIntelVerdict.KNOWN_SAFE

@pytest.mark.asyncio
async def test_multi_threat_intel_mock_malicious():
    provider = MultiThreatIntelProvider()
    res = await provider.lookup("http://secure-bank0findia-verify.xyz/login")
    assert res.verdict == ThreatIntelVerdict.KNOWN_MALICIOUS

@pytest.mark.asyncio
async def test_multi_threat_intel_caching():
    provider = MultiThreatIntelProvider()
    res1 = await provider.lookup("https://www.google.com")
    res2 = await provider.lookup("https://www.google.com")
    assert res1.verdict == res2.verdict
