import asyncio
import logging
import time
from urllib.parse import urlparse
from typing import Dict, Tuple, List, Optional
import httpx

from app.services.threat_intel.base import ThreatIntelProvider, ThreatIntelResult, ThreatIntelVerdict

logger = logging.getLogger(__name__)

class PhishDestroyProvider(ThreatIntelProvider):
    def __init__(self):
        self.base_url = "https://api.destroy.tools/v1/check"
        # Bounded cache with TTL of 10 minutes (600 seconds)
        self._cache: Dict[str, Tuple[ThreatIntelResult, float]] = {}
        self._cache_lock = asyncio.Lock()
        self.ttl = 600.0

    @staticmethod
    def extract_domain(raw_url: str) -> str:
        if not raw_url:
            return ""
        url = raw_url.strip().rstrip(".,;:!?)>\"']")
        if not url.startswith("http://") and not url.startswith("https://"):
            url = "http://" + url
        try:
            parsed = urlparse(url)
            domain = parsed.netloc or parsed.path
            if ":" in domain:
                domain = domain.split(":")[0]
            if domain.startswith("www."):
                domain = domain[4:]
            return domain.lower().strip()
        except Exception:
            return ""

    async def lookup(self, url: str) -> ThreatIntelResult:
        domain = self.extract_domain(url)
        if not domain:
            return ThreatIntelResult(
                provider="phishdestroy",
                checked=True,
                reachable=False,
                threat=False,
                riskScore=0,
                severity=None,
                flags=[],
                matchedKeywords=[],
                error="Invalid URL or domain",
                degraded=True,
                verdict=ThreatIntelVerdict.UNAVAILABLE
            )

        now = time.time()
        async with self._cache_lock:
            if domain in self._cache:
                cached_res, expiry = self._cache[domain]
                if now < expiry:
                    logger.info(f"PhishDestroy Cache Hit for domain: {domain}")
                    return cached_res
                else:
                    del self._cache[domain]

        logger.info(f"PhishDestroy API request for domain: {domain}")
        headers = {"User-Agent": "SancharSaathi/1.0"}
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(
                    f"{self.base_url}?domain={domain}",
                    headers=headers,
                    timeout=5.0
                )
                
                if response.status_code == 200:
                    data = response.json()
                    threat = bool(data.get("threat", False))
                    verdict = ThreatIntelVerdict.CHECKED_THREAT if threat else ThreatIntelVerdict.CHECKED_CLEAN
                    
                    result = ThreatIntelResult(
                        provider="phishdestroy",
                        checked=True,
                        reachable=True,
                        threat=threat,
                        riskScore=int(data.get("risk_score", 0)),
                        severity=data.get("severity"),
                        flags=list(data.get("flags", [])),
                        matchedKeywords=list(data.get("matched_keywords", [])),
                        error=None,
                        degraded=False,
                        verdict=verdict
                    )
                else:
                    # HTTP Error status code
                    result = ThreatIntelResult(
                        provider="phishdestroy",
                        checked=True,
                        reachable=False,
                        threat=False,
                        riskScore=0,
                        severity=None,
                        flags=[],
                        matchedKeywords=[],
                        error=f"HTTP Error {response.status_code}",
                        degraded=True,
                        verdict=ThreatIntelVerdict.UNAVAILABLE
                    )
        except Exception as e:
            # Connection, DNS, or Timeout Error
            logger.error(f"PhishDestroy API request failed: {e}")
            result = ThreatIntelResult(
                provider="phishdestroy",
                checked=True,
                reachable=False,
                threat=False,
                riskScore=0,
                severity=None,
                flags=[],
                matchedKeywords=[],
                error=str(e),
                degraded=True,
                verdict=ThreatIntelVerdict.UNAVAILABLE
            )

        # Cache only if reachable/successful
        if result.reachable:
            async with self._cache_lock:
                self._cache[domain] = (result, now + self.ttl)

        return result

    async def probe_health(self) -> dict:
        """
        Runs a health probe against api.destroy.tools using a known domain (e.g. destroy.tools).
        """
        domain_to_test = "destroy.tools"
        headers = {"User-Agent": "SancharSaathi/1.0"}
        try:
            async with httpx.AsyncClient() as client:
                res = await client.get(
                    f"{self.base_url}?domain={domain_to_test}",
                    headers=headers,
                    timeout=2.0
                )
                if res.status_code == 200:
                    return {"reachable": True, "details": "PhishDestroy Connected"}
                else:
                    return {"reachable": False, "details": f"PhishDestroy HTTP {res.status_code}"}
        except Exception as e:
            return {"reachable": False, "details": str(e)}
