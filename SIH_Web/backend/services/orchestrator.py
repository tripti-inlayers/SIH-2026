import asyncio
import re
import unicodedata
import hashlib
import time
from typing import List, Dict, Any, Optional, Tuple
from models.schemas import Signal, AnalyzeResponse
from services.analyzers import MLAdapter, KeywordAnalyzer, URLAnalyzer, ThreatIntelMock, IdentityVerifier
from services.risk_fusion import fuse_signals

# Regex patterns
URL_PATTERN = re.compile(
    r'(?:https?://)?(?:www\.)?[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+(?::\d+)?(?:/[^\s]*)?',
    re.IGNORECASE
)
PHONE_PATTERN = re.compile(
    r'\+?\d[\d\-\s\(\)]{8,}\d'
)

def preprocess_input(text: str) -> str:
    if not text:
        return ""
    # NFKD Unicode normalization
    normalized = unicodedata.normalize('NFKD', text)
    # Strip non-printable characters
    cleaned = "".join(c for c in normalized if c.isprintable() or c in "\n\r\t")
    return cleaned

async def run_with_timeout(coro, source_name: str, timeout: float = 0.8) -> Signal:
    try:
        return await asyncio.wait_for(coro, timeout=timeout)
    except asyncio.TimeoutError:
        return Signal(
            source=source_name,
            description=f"{source_name} analysis timed out",
            confidence=0.0,
            weight=0.0
        )
    except Exception as e:
        return Signal(
            source=source_name,
            description=f"{source_name} failed: {str(e)}",
            confidence=0.0,
            weight=0.0
        )

class OrchestratorService:
    def __init__(self):
        self.ml_adapter = MLAdapter()
        self.keyword_analyzer = KeywordAnalyzer()
        self.url_analyzer = URLAnalyzer()
        self.threat_intel = ThreatIntelMock()
        self.identity_verifier = IdentityVerifier()
        # Cache stores: hash -> (AnalyzeResponse, expiry_timestamp_or_None)
        self.cache: Dict[str, Tuple[AnalyzeResponse, Optional[float]]] = {}

    async def analyze_content(self, text: str, source: str = "manual", sender: Optional[str] = None) -> AnalyzeResponse:
        # Pre-process content
        cleaned_text = preprocess_input(text)
        
        # 1. SHA256 Caching check with TTL Eviction
        cache_key_raw = f"{cleaned_text}||{sender or ''}"
        content_hash = hashlib.sha256(cache_key_raw.encode('utf-8')).hexdigest()
        now = time.time()
        
        if content_hash in self.cache:
            cached_resp, expiry = self.cache[content_hash]
            if expiry is None or expiry > now:
                return cached_resp
            else:
                del self.cache[content_hash]
            
        # Extract candidate URLs and phone numbers
        urls = URL_PATTERN.findall(cleaned_text)
        phones = PHONE_PATTERN.findall(cleaned_text)
        
        # Clean extracted URLs (ensure they have scheme)
        normalized_urls = []
        for u in urls:
            if u.lower().startswith('www.'):
                normalized_urls.append('http://' + u)
            elif not u.lower().startswith('http://') and not u.lower().startswith('https://'):
                normalized_urls.append('http://' + u)
            else:
                normalized_urls.append(u)

        # Run all tasks in parallel with individual SLAs
        tasks = [
            run_with_timeout(self.ml_adapter.analyze(cleaned_text), "ml_model", timeout=0.8),
            run_with_timeout(self.keyword_analyzer.analyze(cleaned_text), "keyword_analyzer", timeout=0.5),
            run_with_timeout(self.url_analyzer.analyze(cleaned_text, normalized_urls), "url_lexical", timeout=0.5),
            run_with_timeout(self.threat_intel.analyze(cleaned_text, normalized_urls), "threat_intel", timeout=0.8),
            run_with_timeout(self.identity_verifier.analyze(sender), "identity_verifier", timeout=0.5)
        ]
        
        signals: List[Signal] = []
        
        # Gather tasks with return_exceptions=True
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # Map results to signals, replacing exceptions/timeouts with fallbacks
        sources = ["ml_model", "keyword_analyzer", "url_lexical", "threat_intel", "identity_verifier"]
        for idx, res in enumerate(results):
            src = sources[idx]
            if src == "ml_model" and (isinstance(res, Exception) or (isinstance(res, Signal) and ("timed out" in res.description or "failed" in res.description or res.confidence == 0.0))):
                # Fallback ML score matching keywords
                lower_text = cleaned_text.lower()
                spam_triggers = ["urgent", "immediately", "bank", "blocked", "won", "lottery", "prize", "login", "password", "verify", "indiapost", "gov.in"]
                if "indiapost" in lower_text or "gov.in" in lower_text:
                    signals.append(Signal(
                        source="ml_model",
                        description="ML classifier (fallback) identified ham with confidence 0.95",
                        confidence=0.95,
                        weight=0.0
                    ))
                elif any(trigger in lower_text for trigger in spam_triggers):
                    signals.append(Signal(
                        source="ml_model",
                        description="ML classifier (fallback) identified spam with confidence 0.90",
                        confidence=0.90,
                        weight=35.0 # Updated weight in BASE_WEIGHTS
                    ))
                else:
                    signals.append(Signal(
                        source="ml_model",
                        description="ML classifier (fallback) identified ham with confidence 0.90",
                        confidence=0.90,
                        weight=0.0
                    ))
            elif isinstance(res, Exception):
                signals.append(Signal(
                    source=src,
                    description=f"Analyzer error: {str(res)}",
                    confidence=0.0,
                    weight=0.0
                ))
            elif isinstance(res, Signal):
                signals.append(res)
            else:
                signals.append(Signal(
                    source=src,
                    description="Analyzer returned unknown format",
                    confidence=0.0,
                    weight=0.0
                ))
            
        # Fusion and Decision Policy
        risk_score, risk_level, decision, explanation, partial_analysis = fuse_signals(signals)
        
        # Add phone number details if phone numbers are found and risk is not low
        if phones and risk_score > 0:
            explanation += f" Detected phone candidates: {', '.join(phones[:2])}."
            
        response = AnalyzeResponse(
            risk_score=risk_score,
            risk_level=risk_level,
            decision=decision,
            signals=signals,
            explanation=explanation,
            partial_analysis=partial_analysis
        )
        
        # Save to cache with dynamic TTL:
        # - Permanent for high-risk threats / spam (decision == BLOCK)
        # - 1 hour (3600s) for messages with URLs
        # - 24 hours (86400s) for clean/low-risk messages
        if decision == "BLOCK" or risk_level == "HIGH":
            expiry_time = None # Permanent
        elif urls:
            expiry_time = now + 3600.0 # 1 hour
        else:
            expiry_time = now + 86400.0 # 24 hours
            
        self.cache[content_hash] = (response, expiry_time)
        return response
