import httpx
from urllib.parse import urlparse
from app.services.threat_intel.base import ThreatIntelResult, ThreatIntelVerdict
from app.config import settings
from app.core.logging import logger

class RdapThreatIntelProvider:
    async def lookup(self, url: str) -> ThreatIntelResult:
        try:
            parsed = urlparse(url)
            host = (parsed.netloc or "").lower().split(":")[0]
            parts = host.split(".")
            domain = ".".join(parts[-2:]) if len(parts) >= 2 else host
            
            rdap_url = f"https://rdap.org/domain/{domain}"
            async with httpx.AsyncClient(timeout=settings.REQUEST_TIMEOUT_SECONDS) as client:
                resp = await client.get(rdap_url, follow_redirects=True)
                if resp.status_code == 200:
                    data = resp.json()
                    events = data.get("events", [])
                    reg_date = None
                    for event in events:
                        if event.get("eventAction") in ("registration", "transfer"):
                            reg_date = event.get("eventDate")
                            break
                    return ThreatIntelResult(
                        verdict=ThreatIntelVerdict.UNKNOWN,
                        source="rdap",
                        detail=f"RDAP lookup succeeded. Registration date: {reg_date or 'Not specified'}"
                    )
                else:
                    return ThreatIntelResult(
                        verdict=ThreatIntelVerdict.UNKNOWN,
                        source="rdap",
                        detail=f"RDAP lookup returned HTTP {resp.status_code}."
                    )
        except Exception as e:
            logger.debug(f"RDAP lookup failed: {e}")
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="rdap",
                detail=f"RDAP lookup unavailable ({str(e)})"
            )
