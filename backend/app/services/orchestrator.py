import asyncio
import re
import uuid
from typing import List, Optional
from app.schemas.analyze import AnalyzeRequest, RiskResultResponse
from app.schemas.common import RiskSignal
from app.services.message_analysis import MessageAnalysisService
from app.services.url_analysis import UrlAnalysisService
from app.services.threat_intel.base import ThreatIntelVerdict
from app.services.threat_intel.mock_provider import MockThreatIntelProvider
from app.services.threat_intel.rdap_provider import RdapThreatIntelProvider
from app.services.threat_intel.google_webrisk import GoogleWebRiskProvider
from app.services.identity.dlt_mock_provider import DltMockIdentityProvider
from app.services.risk_fusion import RiskFusionEngine
from app.services.ml_analysis import MlAnalysisService
from app.repositories.analysis_repository import get_analysis_repository
from app.config import settings
from app.core.logging import logger

URL_PATTERN = re.compile(r"https?://[^\s<>\"'{}|\\^`\[\]]+", re.IGNORECASE)

def extract_and_deduplicate_urls(urls_list: Optional[List[str]], text: str) -> List[str]:
    """
    Extracts, cleans, and deduplicates URLs from both explicit list and message text.
    """
    candidates = []
    if urls_list:
        candidates.extend(urls_list)
    if text:
        candidates.extend(URL_PATTERN.findall(text))
    
    unique_urls: List[str] = []
    seen = set()
    for raw in candidates:
        if not raw:
            continue
        cleaned = raw.strip().rstrip(".,;:!?)>\"']")
        if cleaned and cleaned not in seen:
            seen.add(cleaned)
            unique_urls.append(cleaned)
    return unique_urls

class AnalysisOrchestrator:
    def __init__(self):
        self.message_service = MessageAnalysisService()
        self.url_service = UrlAnalysisService()
        self.ml_service = MlAnalysisService()
        
        if settings.THREAT_INTEL_PROVIDER == "rdap":
            self.threat_intel_provider = RdapThreatIntelProvider()
        elif settings.THREAT_INTEL_PROVIDER == "google_webrisk" or settings.GOOGLE_WEBRISK_API_KEY:
            self.threat_intel_provider = GoogleWebRiskProvider()
        else:
            self.threat_intel_provider = MockThreatIntelProvider()

        self.identity_provider = DltMockIdentityProvider()
        self.fusion_engine = RiskFusionEngine()
        self.repo = get_analysis_repository()

    async def analyze(self, request: AnalyzeRequest) -> RiskResultResponse:
        degraded = False
        degraded_reasons: List[str] = []
        all_signals: List[RiskSignal] = []

        # 1. Message NLP Signals
        try:
            msg_task = asyncio.create_task(
                asyncio.to_thread(self.message_service.analyze, request.text, request.claimed_organization)
            )
            msg_signals = await asyncio.wait_for(msg_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
            all_signals.extend(msg_signals)
        except Exception as e:
            logger.error(f"Message analysis failed or timed out: {e}")
            degraded = True
            degraded_reasons.append("message_analysis_timeout")

        # 1.5. ML Model Signals (RoBERTa fine-tuned)
        try:
            ml_task = asyncio.create_task(self.ml_service.analyze(request.text))
            ml_signal = await asyncio.wait_for(ml_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
            if ml_signal:
                all_signals.append(ml_signal)
        except Exception as e:
            logger.error(f"ML analysis failed or timed out: {e}")
            degraded = True
            degraded_reasons.append("ml_analysis_timeout")

        # Extract and deduplicate all URLs (from request list and text)
        urls = extract_and_deduplicate_urls(request.urls, request.text)
        primary_url = urls[0] if urls else None

        # 2. URL Lexical & Heuristic Signals (Concurrent for all URLs)
        if urls:
            try:
                url_tasks = [
                    asyncio.to_thread(self.url_service.analyze, u)
                    for u in urls
                ]
                url_results = await asyncio.gather(*url_tasks, return_exceptions=True)
                for r in url_results:
                    if isinstance(r, list):
                        all_signals.extend(r)
                    elif isinstance(r, Exception):
                        logger.error(f"URL analysis error: {r}")
                        degraded = True
                        if "url_analysis_error" not in degraded_reasons:
                            degraded_reasons.append("url_analysis_error")
            except Exception as e:
                logger.error(f"URL analysis failed or timed out: {e}")
                degraded = True
                degraded_reasons.append("url_analysis_timeout")

        # 3. Threat Intelligence Signals (Google Web Risk / Provider - Concurrent for all URLs)
        if urls:
            try:
                threat_tasks = [
                    self.threat_intel_provider.lookup(u)
                    for u in urls
                ]
                threat_results = await asyncio.gather(*threat_tasks, return_exceptions=True)
                for u, res in zip(urls, threat_results):
                    if isinstance(res, Exception):
                        logger.error(f"Threat intel error for {u}: {res}")
                        degraded = True
                        if "threat_intel_error" not in degraded_reasons:
                            degraded_reasons.append("threat_intel_error")
                        continue

                    if not res.available:
                        degraded = True
                        if "threat_intel_unavailable" not in degraded_reasons:
                            degraded_reasons.append("threat_intel_unavailable")
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="WEBRISK_UNAVAILABLE",
                            description="Threat intelligence lookup unavailable.",
                            technical_detail=f"Provider '{res.source}': {res.detail}",
                            weight=0.0,
                            triggered=False
                        ))
                    elif res.matched:
                        threat_types = res.threat_types
                        has_malware = "MALWARE" in threat_types
                        has_phishing = "SOCIAL_ENGINEERING" in threat_types
                        has_unwanted = "UNWANTED_SOFTWARE" in threat_types
                        has_extended = "SOCIAL_ENGINEERING_EXTENDED_COVERAGE" in threat_types

                        if has_malware:
                            all_signals.append(RiskSignal(
                                category="threat_intel",
                                code="WEBRISK_MALWARE",
                                description="Google Web Risk identified this URL as associated with malware distribution.",
                                technical_detail=f"URL: {u} | Threat: MALWARE",
                                weight=0.80,
                                triggered=True
                            ))
                        if has_phishing:
                            all_signals.append(RiskSignal(
                                category="threat_intel",
                                code="WEBRISK_PHISHING",
                                description="Google Web Risk identified this URL as associated with social engineering or phishing.",
                                technical_detail=f"URL: {u} | Threat: SOCIAL_ENGINEERING",
                                weight=0.80,
                                triggered=True
                            ))
                        if has_unwanted:
                            all_signals.append(RiskSignal(
                                category="threat_intel",
                                code="WEBRISK_UNWANTED_SOFTWARE",
                                description="Google Web Risk identified this URL as associated with unwanted software.",
                                technical_detail=f"URL: {u} | Threat: UNWANTED_SOFTWARE",
                                weight=0.50,
                                triggered=True
                            ))
                        if has_extended and not (has_malware or has_phishing or has_unwanted):
                            all_signals.append(RiskSignal(
                                category="threat_intel",
                                code="WEBRISK_EXTENDED_COVERAGE",
                                description="Google Web Risk extended coverage flagged this URL as potential social engineering risk.",
                                technical_detail=f"URL: {u} | Threat: SOCIAL_ENGINEERING_EXTENDED_COVERAGE",
                                weight=0.35,
                                triggered=True
                            ))

                        if not (has_malware or has_phishing or has_unwanted or has_extended):
                            all_signals.append(RiskSignal(
                                category="threat_intel",
                                code="REPUTATION_MALICIOUS",
                                description="This domain/URL is flagged as known malicious by threat intelligence.",
                                technical_detail=f"Provider '{res.source}': {res.detail}",
                                weight=0.80,
                                triggered=True
                            ))
                    else:
                        # Clean / No match on threat lists - Informative neutral evidence (NEVER forces score to 0)
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="REPUTATION_UNKNOWN",
                            description="Google Web Risk: No matching threat found in threat lists.",
                            technical_detail=f"Provider '{res.source}': clean/no match for {u}",
                            weight=0.0,
                            triggered=False
                        ))
            except Exception as e:
                logger.error(f"Threat intel batch failed or timed out: {e}")
                degraded = True
                degraded_reasons.append("threat_intel_timeout")

        # 4. Identity Verification Signals
        try:
            id_task = asyncio.create_task(
                self.identity_provider.verify(request.sender_id, request.claimed_organization, urls)
            )
            id_signals = await asyncio.wait_for(id_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
            all_signals.extend(id_signals)
        except Exception as e:
            logger.error(f"Identity verification failed or timed out: {e}")
            degraded = True
            degraded_reasons.append("identity_verification_timeout")

        # 5. Risk Fusion
        score, level, confidence, reasons, action, should_block, should_report = self.fusion_engine.fuse(
            signals=all_signals,
            has_url=bool(urls),
            degraded=degraded
        )

        analysis_id = str(uuid.uuid4())
        response = RiskResultResponse(
            analysis_id=analysis_id,
            risk_score=score,
            risk_level=level,
            confidence=confidence,
            reasons=reasons,
            signals=all_signals,
            recommended_action=action,
            should_block=should_block,
            should_report=should_report,
            detected_url=primary_url,
            sender=request.sender_id,
            model_version="1.0.0",
            degraded=degraded,
            degraded_reason=",".join(degraded_reasons) if degraded_reasons else None
        )

        # Persist analysis
        await self.repo.save(response, request)
        return response
