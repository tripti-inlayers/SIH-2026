import httpx
from typing import Optional
from app.services.threat_intel.base import ThreatIntelResult, ThreatIntelVerdict
from app.config import settings
from app.core.logging import logger

_UNSET = object()

class GoogleWebRiskProvider:
    """Live integration with Google Web Risk API."""

    def __init__(self, api_key: Optional[str] = _UNSET):
        if api_key is _UNSET:
            self.api_key = settings.GOOGLE_WEBRISK_API_KEY
        else:
            self.api_key = api_key

    async def lookup(self, url: str) -> ThreatIntelResult:
        if not self.api_key:
            logger.debug("Google Web Risk API key missing. Skipping Web Risk lookup.")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_webrisk",
                detail="Google Web Risk API key missing (skipping lookup)."
            )

        api_url = "https://webrisk.googleapis.com/v1/uris:search"
        params = {
            "key": self.api_key,
            "uri": url,
            "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE"]
        }

        try:
            async with httpx.AsyncClient(timeout=settings.THREAT_INTEL_TIMEOUT_SECONDS) as client:
                resp = await client.get(api_url, params=params)
                if resp.status_code == 200:
                    data = resp.json()
                    threat = data.get("threat")
                    if threat and threat.get("threatTypes"):
                        threat_types = ", ".join(threat["threatTypes"])
                        return ThreatIntelResult(
                            verdict=ThreatIntelVerdict.KNOWN_MALICIOUS,
                            source="google_webrisk",
                            detail=f"Google Web Risk flagged URL as malicious ({threat_types})."
                        )
                    else:
                        return ThreatIntelResult(
                            verdict=ThreatIntelVerdict.UNKNOWN,
                            source="google_webrisk",
                            detail="Google Web Risk returned no threat match."
                        )
                else:
                    logger.warning(f"Google Web Risk returned status code {resp.status_code}")
                    return ThreatIntelResult(
                        verdict=ThreatIntelVerdict.UNKNOWN,
                        source="google_webrisk",
                        detail=f"Google Web Risk returned HTTP {resp.status_code}."
                    )
        except Exception as e:
            logger.error(f"Google Web Risk lookup failed: {e}")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_webrisk",
                detail=f"Google Web Risk lookup unavailable ({str(e)})"
            )
