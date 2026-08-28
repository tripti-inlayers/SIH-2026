import asyncio
import hashlib
import logging
import time
from datetime import datetime
from typing import Dict, Any, List, Optional, Tuple
import httpx

from app.config import settings
from app.services.threat_intel.base import ThreatIntelProvider, ThreatIntelResult, ThreatIntelVerdict

logger = logging.getLogger(__name__)

THREAT_TYPES = [
    "MALWARE",
    "SOCIAL_ENGINEERING",
    "UNWANTED_SOFTWARE",
    "SOCIAL_ENGINEERING_EXTENDED_COVERAGE"
]

class GoogleWebRiskProvider(ThreatIntelProvider):
    def __init__(self):
        self.api_key = getattr(settings, "GOOGLE_WEBRISK_API_KEY", None)
        self.base_url = "https://webrisk.googleapis.com/v1/uris:search"
        self._cache: Dict[str, Tuple[Dict[str, Any], float]] = {}
        self._cache_lock = asyncio.Lock()

    @staticmethod
    def normalize_url(raw_url: str) -> str:
        """
        Cleans and normalizes URL for Web Risk lookup:
        - Trims whitespace
        - Strips accidental trailing punctuation
        - Ensures standard scheme
        """
        if not raw_url:
            return ""
        url = raw_url.strip()
        url = url.rstrip(".,;:!?)>\"']")
        if not url.startswith("http://") and not url.startswith("https://"):
            url = "https://" + url
        return url

    async def check_url(self, raw_url: str) -> Dict[str, Any]:
        """
        Checks a URL against Google Web Risk Lookup API.
        Returns normalized dictionary:
        {
            "provider": "google_web_risk",
            "available": bool,
            "matched": bool,
            "threat_types": List[str],
            "expire_time": Optional[str],
            "error": Optional[str]
        }
        """
        url = self.normalize_url(raw_url)
        if not url:
            return {
                "provider": "google_web_risk",
                "available": False,
                "matched": False,
                "threat_types": [],
                "error": "Empty or invalid URL"
            }

        # Check Cache
        now = time.time()
        async with self._cache_lock:
            if url in self._cache:
                cached_res, expiry = self._cache[url]
                if now < expiry:
                    logger.debug("WEBRISK_CACHE_HIT for URL")
                    return cached_res
                else:
                    del self._cache[url]

        if not self.api_key:
            logger.warning("Google Web Risk API key not configured.")
            return {
                "provider": "google_web_risk",
                "available": False,
                "matched": False,
                "threat_types": [],
                "error": "API key not configured."
            }

        url_hash_prefix = hashlib.sha256(url.encode()).hexdigest()[:12]
        logger.info(f"WEBRISK_REQUEST url_hash={url_hash_prefix}")

        params = [("uri", url)]
        for t in THREAT_TYPES:
            params.append(("threatTypes", t))

        headers = {
            "x-goog-api-key": self.api_key
        }

        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(
                    self.base_url,
                    params=params,
                    headers=headers,
                    timeout=5.0
                )
                
                # If header auth returns 400/401/403 with specific key error, fallback to query param
                if response.status_code in (400, 401, 403) and "key" in response.text.lower():
                    params_with_key = params + [("key", self.api_key)]
                    response = await client.get(
                        self.base_url,
                        params=params_with_key,
                        timeout=5.0
                    )

                if response.status_code == 200:
                    data = response.json()
                    
                    matched = "threat" in data and bool(data["threat"].get("threatTypes"))
                    threat_types = data["threat"].get("threatTypes", []) if matched else []
                    expire_time_str = data["threat"].get("expireTime") if matched else None

                    ttl = 300.0  # 5 minutes default for clean/no-match URLs
                    if matched:
                        ttl = 1800.0  # 30 minutes for matched threats
                        if expire_time_str:
                            try:
                                dt = datetime.fromisoformat(expire_time_str.replace("Z", "+00:00"))
                                ttl_calc = dt.timestamp() - now
                                if 60 < ttl_calc < 86400:
                                    ttl = ttl_calc
                            except Exception:
                                pass

                    result = {
                        "provider": "google_web_risk",
                        "available": True,
                        "matched": matched,
                        "threat_types": threat_types,
                        "expire_time": expire_time_str,
                        "error": None
                    }

                    # Cache successful lookup
                    async with self._cache_lock:
                        self._cache[url] = (result, now + ttl)

                    logger.info(f"WEBRISK_RESPONSE available=True matched={matched} threatTypes={threat_types}")
                    return result
                else:
                    logger.error(f"Google Web Risk API HTTP error: {response.status_code}")
                    return {
                        "provider": "google_web_risk",
                        "available": False,
                        "matched": False,
                        "threat_types": [],
                        "error": f"API returned status {response.status_code}"
                    }

        except httpx.TimeoutException:
            logger.error("Google Web Risk request timed out")
            return {
                "provider": "google_web_risk",
                "available": False,
                "matched": False,
                "threat_types": [],
                "error": "Request timed out"
            }
        except Exception as e:
            logger.error(f"Google Web Risk lookup failed: {e}")
            return {
                "provider": "google_web_risk",
                "available": False,
                "matched": False,
                "threat_types": [],
                "error": "Connection or lookup error"
            }

    async def lookup(self, url: str) -> ThreatIntelResult:
        """
        Protocol implementation returning ThreatIntelResult.
        """
        res = await self.check_url(url)
        if not res["available"]:
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,
                source="google_webrisk",
                detail=f"Google Web Risk unavailable ({res.get('error', 'error')})",
                available=False,
                matched=False,
                threat_types=[],
                error=res.get("error")
            )
        
        if res["matched"]:
            threat_names = []
            for t in res["threat_types"]:
                if t == "SOCIAL_ENGINEERING":
                    threat_names.append("Social engineering / phishing")
                elif t == "MALWARE":
                    threat_names.append("Malware distribution")
                elif t == "UNWANTED_SOFTWARE":
                    threat_names.append("Unwanted software")
                elif t == "SOCIAL_ENGINEERING_EXTENDED_COVERAGE":
                    threat_names.append("Extended coverage social engineering")
                else:
                    threat_names.append(t)

            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.KNOWN_MALICIOUS,
                source="google_webrisk",
                detail=f"Potentially unsafe link detected. Flagged by Google Web Risk as: {', '.join(threat_names)}",
                available=True,
                matched=True,
                threat_types=res["threat_types"],
                expire_time=res.get("expire_time")
            )
        else:
            return ThreatIntelResult(
                verdict=ThreatIntelVerdict.UNKNOWN,  # Not evidence of absolute safety
                source="google_webrisk",
                detail="Google Web Risk: No matching threat found",
                available=True,
                matched=False,
                threat_types=[]
            )
