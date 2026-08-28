import asyncio
import re
import uuid
from typing import List, Optional
from app.schemas.analyze import AnalyzeRequest, RiskResultResponse, ThreatIntelInfo
from app.schemas.common import RiskSignal
from app.services.message_analysis import MessageAnalysisService
from app.services.url_analysis import UrlAnalysisService
from app.services.threat_intel.base import ThreatIntelVerdict
from app.services.threat_intel.mock_provider import MockThreatIntelProvider
from app.services.threat_intel.rdap_provider import RdapThreatIntelProvider
from app.services.threat_intel.phish_destroy import PhishDestroyProvider
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
        else:
            self.threat_intel_provider = PhishDestroyProvider()

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

        # 3. Threat Intelligence / PhishDestroy
        primary_threat_intel_info = ThreatIntelInfo(
            provider="phishdestroy",
            checked=bool(urls),
            reachable=False,
            threat=False,
            risk_score=0,
            severity=None,
            flags=[],
            matched_keywords=[],
            error="No URLs in content" if not urls else None,
            degraded=False,
            verdict="UNAVAILABLE"
        )

        max_pd_score = 0
        any_pd_threat = False

        if urls:
            try:
                threat_tasks = [
                    asyncio.wait_for(
                        self.threat_intel_provider.lookup(u),
                        timeout=settings.REQUEST_TIMEOUT_SECONDS
                    )
                    for u in urls
                ]
                threat_results = await asyncio.gather(*threat_tasks, return_exceptions=True)
                
                best_result = None
                
                for u, res in zip(urls, threat_results):
                    if isinstance(res, Exception):
                        logger.error(f"Threat intel error for {u}: {res}")
                        degraded = True
                        if "threat_intel_error" not in degraded_reasons:
                            degraded_reasons.append("threat_intel_error")
                        primary_threat_intel_info.error = f"Threat intel lookup error ({str(res)})"
                        continue

                    if not res.reachable:
                        degraded = True
                        if "threat_intel_unavailable" not in degraded_reasons:
                            degraded_reasons.append("threat_intel_unavailable")
                        
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="REPUTATION_UNAVAILABLE",
                            description="PhishDestroy lookup unavailable or degraded.",
                            technical_detail=f"Provider 'phishdestroy' lookup failed for {u}: {res.error}",
                            weight=0.0,
                            triggered=False
                        ))
                        continue

                    # Accumulate stats
                    max_pd_score = max(max_pd_score, res.riskScore)
                    if res.threat:
                        any_pd_threat = True

                    # Select best_result
                    if best_result is None:
                        best_result = res
                    else:
                        if res.threat and not best_result.threat:
                            best_result = res
                        elif res.riskScore > best_result.riskScore:
                            best_result = res

                    # Add signal for each URL checked
                    if res.threat:
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="REPUTATION_MALICIOUS",
                            description=f"PhishDestroy flagged threat for URL: {u}",
                            technical_detail=f"Domain: {u} | threat={res.threat} | score={res.riskScore} | flags={res.flags}",
                            weight=res.riskScore / 100.0,
                            triggered=True
                        ))
                    else:
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="REPUTATION_UNKNOWN",
                            description=f"PhishDestroy: No threat match for URL: {u}",
                            technical_detail=f"Domain: {u} | clean | score={res.riskScore}",
                            weight=0.0,
                            triggered=False
                        ))

                if best_result is not None:
                    primary_threat_intel_info = ThreatIntelInfo(
                        provider="phishdestroy",
                        checked=True,
                        reachable=best_result.reachable,
                        threat=best_result.threat,
                        risk_score=best_result.riskScore,
                        severity=best_result.severity,
                        flags=best_result.flags,
                        matched_keywords=best_result.matchedKeywords,
                        error=best_result.error,
                        degraded=best_result.degraded,
                        verdict=best_result.verdict.value
                    )
            except Exception as e:
                logger.error(f"Threat intel batch failed or timed out: {e}")
                degraded = True
                degraded_reasons.append("threat_intel_timeout")
                primary_threat_intel_info.error = f"Threat intel batch timeout: {str(e)}"
                primary_threat_intel_info.degraded = True
                primary_threat_intel_info.verdict = "UNAVAILABLE"

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

        # Extract ML spam probability if available
        ml_spam_prob = None
        for s in all_signals:
            if s.category == "ml_model":
                match = re.search(r"confidence:\s*([\d\.]+)", s.technical_detail)
                if match:
                    conf = float(match.group(1))
                    if s.code == "AI_SPAM_DETECTED":
                        ml_spam_prob = conf
                    else:
                        ml_spam_prob = 1.0 - conf

        # 5. Risk Fusion
        score, level, confidence, reasons, action, should_block, should_report = self.fusion_engine.fuse(
            signals=all_signals,
            has_url=bool(urls),
            degraded=degraded,
            phishdestroy_score=max_pd_score,
            phishdestroy_threat=any_pd_threat,
            ml_spam_probability=ml_spam_prob
        )

        analysis_id = request.message_id or str(uuid.uuid4())
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
            degraded_reason=",".join(degraded_reasons) if degraded_reasons else None,
            threat_intel=primary_threat_intel_info
        )

        # Persist analysis
        await self.repo.save(response, request)
        return response
