from app.services.threat_intel.base import ThreatIntelProvider, ThreatIntelResult, ThreatIntelVerdict
from urllib.parse import urlparse

DEMO_SAFE_DOMAINS = {"indiapost.gov.in", "www.indiapost.gov.in"}
DEMO_MALICIOUS_DOMAINS = {"secure-bank0findia-verify.xyz", "bank0findia.xyz"}

class MockThreatIntelProvider:
    async def lookup(self, url: str) -> ThreatIntelResult:
        try:
            parsed = urlparse(url)
            host = (parsed.netloc or "").lower().split(":")[0]
        except Exception:
            host = url.lower()

        if host in DEMO_SAFE_DOMAINS:
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.KNOWN_SAFE,
                source="mock",
                detail="Domain matched mock clean domain database."
            )
        elif host in DEMO_MALICIOUS_DOMAINS:
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.KNOWN_MALICIOUS,
                source="mock",
                detail="Domain matched mock threat intelligence malicious database."
            )
        else:
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="mock",
                detail="No threat intelligence record found (UNKNOWN)."
            )

class GoogleWebRiskProviderStub:
    """Stub showing extension point for Google Web Risk API integration."""
    async def lookup(self, url: str) -> ThreatIntelResult:
        raise NotImplementedError("Requires API credentials — see README for live integration instructions.")

class OpenPhishProviderStub:
    """Stub showing extension point for OpenPhish API integration."""
    async def lookup(self, url: str) -> ThreatIntelResult:
        raise NotImplementedError("Requires API credentials — see README for live integration instructions.")
