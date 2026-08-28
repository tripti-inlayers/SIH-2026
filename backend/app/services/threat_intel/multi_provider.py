import asyncio
import time
from typing import Dict, Tuple
from app.services.threat_intel.base import ThreatIntelProvider, ThreatIntelResult, ThreatIntelVerdict
from app.services.threat_intel.google_safebrowsing import GoogleSafeBrowsingProvider
from app.services.threat_intel.phishtank import PhishTankProvider
from app.services.threat_intel.mock_provider import MockThreatIntelProvider
from app.config import settings
from app.core.logging import logger

# In-memory TTL cache: url -> (ThreatIntelResult, expire_timestamp)
_threat_intel_cache: Dict[str, Tuple[ThreatIntelResult, float]] = {}

class MultiThreatIntelProvider:
    """
    Executes multiple Threat Intelligence providers concurrently (Google Safe Browsing v4, PhishTank, Mock)
    with hard timeouts, in-memory TTL caching, and graceful fallbacks.
    """

    def __init__(self):
        self.safebrowsing_provider = GoogleSafeBrowsingProvider()
        self.phishtank_provider = PhishTankProvider()
        self.mock_provider = MockThreatIntelProvider()

    async def lookup(self, url: str) -> ThreatIntelResult:
        if not url:
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="multi",
                detail="Empty URL provided."
            )

        now = time.time()
        # Check cache
        if url in _threat_intel_cache:
            cached_result, expire_at = _threat_intel_cache[url]
            if now < expire_at:
                logger.debug(f"Threat intel cache hit for '{url}': {cached_result.verdict}")
                return cached_result

        # Run providers concurrently
        tasks = [
            self.safebrowsing_provider.lookup(url),
            self.phishtank_provider.lookup(url),
            self.mock_provider.lookup(url)
        ]

        results = await asyncio.gather(*tasks, return_exceptions=True)

        final_verdict = ThreatIntelVerdict.UNKNOWN
        sources: list[str] = []
        details: list[str] = []

        for res in results:
            if isinstance(res, ThreatIntelResult):
                sources.append(res.source)
                details.append(res.detail)
                if res.verdict == ThreatIntelVerdict.KNOWN_MALICIOUS:
                    final_verdict = ThreatIntelVerdict.KNOWN_MALICIOUS
                elif res.verdict == ThreatIntelVerdict.KNOWN_SAFE and final_verdict != ThreatIntelVerdict.KNOWN_MALICIOUS:
                    final_verdict = ThreatIntelVerdict.KNOWN_SAFE

        combined_detail = " | ".join(details)
        result = ThreatIntelResult(
            verdict=final_verdict,
            source="+".join(sources),
            detail=combined_detail
        )

        # Cache result with TTL
        _threat_intel_cache[url] = (result, now + settings.URL_CACHE_TTL_SECONDS)
        return result
