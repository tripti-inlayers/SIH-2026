import httpx
import logging
from urllib.parse import quote_plus
from app.services.threat_intel.base import ThreatIntelProvider, ThreatIntelResult, ThreatIntelVerdict
from app.config import settings

logger = logging.getLogger(__name__)

class GoogleWebRiskProvider(ThreatIntelProvider):
    def __init__(self):
        self.api_key = getattr(settings, "GOOGLE_WEBRISK_API_KEY", None)
        self.base_url = "https://webrisk.googleapis.com/v1/uris:search"

    async def lookup(self, url: str) -> ThreatIntelResult:
        if not self.api_key:
            logger.warning("Google Web Risk API key is missing. Falling back to UNKNOWN.")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_webrisk",
                detail="API key not configured."
            )

        try:
            params = {
                "key": self.api_key,
                "uri": url,
                "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE"]
            }
            async with httpx.AsyncClient() as client:
                response = await client.get(self.base_url, params=params, timeout=5.0)
                response.raise_for_status()
                data = response.json()
                
                # The Web Risk API returns a 'threat' object if a threat is found.
                if "threat" in data:
                    threat_types = data["threat"].get("threatTypes", [])
                    return ThreatIntelResult(
                        verdict=ThreatIntelVerdict.KNOWN_MALICIOUS,
                        source="google_webrisk",
                        detail=f"Flagged by Google Web Risk as: {', '.join(threat_types)}"
                    )
                else:
                    return ThreatIntelResult(
                        verdict=ThreatIntelVerdict.UNKNOWN,  # Web Risk doesn't guarantee safety
                        source="google_webrisk",
                        detail="No threat found in Google Web Risk database."
                    )
        except httpx.HTTPStatusError as e:
            logger.error(f"Google Web Risk API error: {e.response.status_code}")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_webrisk",
                detail=f"API returned status {e.response.status_code}"
            )
        except Exception as e:
            logger.error(f"Google Web Risk lookup failed: {e}")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_webrisk",
                detail="Connection error"
            )
