import httpx
from typing import Optional
from app.services.threat_intel.base import ThreatIntelResult, ThreatIntelVerdict
from app.config import settings
from app.core.logging import logger

_UNSET = object()

class GoogleSafeBrowsingProvider:
    """Live integration with Google Safe Browsing API v4."""

    def __init__(self, api_key: Optional[str] = _UNSET):
        if api_key is _UNSET:
            self.api_key = settings.GOOGLE_SAFE_BROWSING_API_KEY
        else:
            self.api_key = api_key

    async def lookup(self, url: str) -> ThreatIntelResult:
        if not self.api_key:
            logger.debug("Google Safe Browsing API key missing. Skipping Safe Browsing lookup.")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_safebrowsing",
                detail="Google Safe Browsing API key missing (skipping lookup)."
            )

        endpoint = f"https://safebrowsing.googleapis.com/v4/threatMatches:find?key={self.api_key}"
        payload = {
            "client": {
                "clientId": "sanchar-saathi-app",
                "clientVersion": "1.0.0"
            },
            "threatInfo": {
                "threatTypes": [
                    "MALWARE",
                    "SOCIAL_ENGINEERING",
                    "UNWANTED_SOFTWARE",
                    "POTENTIALLY_HARMFUL_APPLICATION"
                ],
                "platformTypes": ["ANY_PLATFORM"],
                "threatEntryTypes": ["URL"],
                "threatEntries": [
                    {"url": url}
                ]
            }
        }

        try:
            async with httpx.AsyncClient(timeout=settings.THREAT_INTEL_TIMEOUT_SECONDS) as client:
                resp = await client.post(endpoint, json=payload)
                if resp.status_code == 200:
                    data = resp.json()
                    matches = data.get("matches", [])
                    if matches:
                        threat_types = ", ".join({m.get("threatType", "THREAT") for m in matches})
                        return ThreatIntelResult(
                            verdict=ThreatIntelVerdict.KNOWN_MALICIOUS,
                            source="google_safebrowsing",
                            detail=f"Google Safe Browsing flagged URL as malicious ({threat_types})."
                        )
                    else:
                        return ThreatIntelResult(
                            verdict=ThreatIntelVerdict.UNKNOWN,
                            source="google_safebrowsing",
                            detail="Google Safe Browsing returned no threat match."
                        )
                else:
                    logger.warning(f"Google Safe Browsing returned status code {resp.status_code}")
                    return ThreatIntelResult(
                        verdict=ThreatIntelVerdict.UNKNOWN,
                        source="google_safebrowsing",
                        detail=f"Google Safe Browsing returned HTTP {resp.status_code}."
                    )
        except Exception as e:
            logger.error(f"Google Safe Browsing lookup failed: {e}")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_safebrowsing",
                detail=f"Google Safe Browsing lookup unavailable ({str(e)})"
            )
