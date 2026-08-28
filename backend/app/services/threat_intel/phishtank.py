import httpx
from urllib.parse import quote
from app.services.threat_intel.base import ThreatIntelResult, ThreatIntelVerdict
from app.config import settings
from app.core.logging import logger

class PhishTankProvider:
    """Live integration with PhishTank API."""

    def __init__(self, api_key: str = None):
        self.api_key = api_key or settings.PHISHTANK_API_KEY

    async def lookup(self, url: str) -> ThreatIntelResult:
        endpoint = "https://checkurl.phishtank.com/checkurl/"
        payload = {
            "url": url,
            "format": "json"
        }
        if self.api_key:
            payload["app_key"] = self.api_key

        headers = {
            "User-Agent": "phishtank/SancharSaathi-Security-App"
        }

        try:
            async with httpx.AsyncClient(timeout=settings.THREAT_INTEL_TIMEOUT_SECONDS) as client:
                resp = await client.post(endpoint, data=payload, headers=headers)
                if resp.status_code == 200:
                    data = resp.json()
                    results = data.get("results", {})
                    url_res = results.get("url") or results.get(url, {})
                    if isinstance(url_res, dict) and url_res.get("in_database") and url_res.get("valid"):
                        return ThreatIntelResult(
                            verdict=ThreatIntelVerdict.KNOWN_MALICIOUS,
                            source="phishtank",
                            detail=f"PhishTank confirmed active phishing link (phish_id: {url_res.get('phish_detail_page')})."
                        )
                    else:
                        return ThreatIntelResult(
                            verdict=ThreatIntelVerdict.UNKNOWN,
                            source="phishtank",
                            detail="PhishTank found no verified active phishing record."
                        )
                else:
                    logger.debug(f"PhishTank returned status code {resp.status_code}")
                    return ThreatIntelResult(
                        verdict=ThreatIntelVerdict.UNKNOWN,
                        source="phishtank",
                        detail=f"PhishTank lookup returned HTTP {resp.status_code}."
                    )
        except Exception as e:
            logger.debug(f"PhishTank lookup failed: {e}")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="phishtank",
                detail=f"PhishTank lookup unavailable ({str(e)})"
            )
