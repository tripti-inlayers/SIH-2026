import asyncio
import uuid
import re
from typing import List, Optional
from app.schemas.analyze import AnalyzeRequest, RiskResultResponse, ThreatIntelInfo
from app.schemas.common import RiskSignal
from app.services.message_analysis import MessageAnalysisService
from app.services.url_analysis import UrlAnalysisService
from app.services.url_expansion import UrlExpanderService, ExpandedUrlResult
from app.services.threat_intel.base import ThreatIntelVerdict
from app.services.threat_intel.multi_provider import MultiThreatIntelProvider
from app.services.identity.dlt_mock_provider import DltMockIdentityProvider
from app.services.identity.trai_registry import TraiHeaderRegistryProvider
from app.services.risk_fusion import RiskFusionEngine
from app.services.ml_analysis import MlAnalysisService
from app.repositories.analysis_repository import get_analysis_repository
from app.config import settings
from app.core.logging import logger

class AnalysisOrchestrator:
    def __init__(self):
        self.message_service = MessageAnalysisService()
        self.url_service = UrlAnalysisService()
        self.url_expander = UrlExpanderService()
        self.ml_service = MlAnalysisService()
        self.threat_intel_provider = MultiThreatIntelProvider()
        self.identity_provider = DltMockIdentityProvider()
        self.trai_provider = TraiHeaderRegistryProvider()
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

        # 1.5. ML Model Signals (Text sequence classification)
        try:
            ml_task = asyncio.create_task(self.ml_service.analyze(request.text))
            ml_signal = await asyncio.wait_for(ml_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
            if ml_signal:
                all_signals.append(ml_signal)
        except Exception as e:
            logger.error(f"ML analysis failed or timed out: {e}")
            degraded = True
            degraded_reasons.append("ml_analysis_timeout")

        # Fallback URL Extractor: ensure domain-first URLs in request.text are parsed even if request.urls is empty
        target_urls = list(request.urls or [])
        url_pattern = r"(?:https?://|cutt\.ly/|bit\.ly/|tinyurl\.com/|t\.co/|(?:[a-zA-Z0-9-]+\.)+(?:com|ly|in|org|net|xyz|tk|top|io|co|gov|edu)/)[^\s]+"
        for match in re.findall(url_pattern, request.text, re.IGNORECASE):
            normalized = match if match.lower().startswith(("http://", "https://")) else f"https://{match}"
            if normalized not in target_urls:
                target_urls.append(normalized)

        # 2. URL Processing for ALL URLs in target_urls
        resolved_urls: List[ExpandedUrlResult] = []
        if target_urls:
            # Expand all short links concurrently
            try:
                expansion_tasks = [self.url_expander.expand(u) for u in target_urls]
                resolved_urls = await asyncio.gather(*expansion_tasks, return_exceptions=False)
            except Exception as e:
                logger.error(f"Shortener expansion failed: {e}")
                # Fallback to original URLs
                resolved_urls = [
                    ExpandedUrlResult(original_url=u, resolved_url=u, redirect_chain=[u], is_shortened=False, hops=0)
                    for u in target_urls
                ]

            for exp in resolved_urls:
                if exp.is_shortened and exp.hops > 0:
                    all_signals.append(RiskSignal(
                        category="url",
                        code="URL_EXPANDED",
                        description=f"Shortened link expanded ({exp.hops} redirects): {exp.original_url} → {exp.resolved_url}",
                        technical_detail=f"Redirect chain: {' -> '.join(exp.redirect_chain)}",
                        weight=0.10,
                        triggered=True
                    ))

                # Analyze heuristics on resolved URL
                try:
                    url_task = asyncio.create_task(
                        asyncio.to_thread(self.url_service.analyze, exp.resolved_url, exp.original_url)
                    )
                    url_signals = await asyncio.wait_for(url_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
                    all_signals.extend(url_signals)
                except Exception as e:
                    logger.error(f"URL analysis failed for '{exp.resolved_url}': {e}")
                    degraded = True
                    degraded_reasons.append("url_analysis_timeout")

        # 3. Concurrent Threat Intelligence Lookup for ALL resolved URLs
        primary_threat_intel_info: Optional[ThreatIntelInfo] = None
        if resolved_urls:
            try:
                intel_tasks = [self.threat_intel_provider.lookup(exp.resolved_url) for exp in resolved_urls]
                intel_results = await asyncio.gather(*intel_tasks, return_exceptions=True)

                for idx, threat_res in enumerate(intel_results):
                    if isinstance(threat_res, Exception):
                        logger.error(f"Threat intel lookup failed: {threat_res}")
                        continue

                    target_exp = resolved_urls[idx]
                    verdict = threat_res.verdict
                    if idx == 0:
                        primary_threat_intel_info = ThreatIntelInfo(
                            provider=threat_res.source,
                            checked=True,
                            reachable=True,
                            threat=(verdict == ThreatIntelVerdict.KNOWN_MALICIOUS),
                            risk_score=85 if verdict == ThreatIntelVerdict.KNOWN_MALICIOUS else 0,
                            severity="CRITICAL" if verdict == ThreatIntelVerdict.KNOWN_MALICIOUS else "LOW",
                            flags=[threat_res.detail] if threat_res.detail else [],
                            matched_keywords=[],
                            error=None,
                            degraded=False,
                            verdict=verdict.value
                        )

                    if verdict == ThreatIntelVerdict.KNOWN_MALICIOUS:
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="REPUTATION_MALICIOUS",
                            description=f"Domain '{target_exp.resolved_url}' is flagged as known malicious by threat intelligence.",
                            technical_detail=f"Providers '{threat_res.source}': {threat_res.detail}",
                            weight=0.85,
                            triggered=True
                        ))
                    elif verdict == ThreatIntelVerdict.KNOWN_SAFE:
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="REPUTATION_SAFE",
                            description=f"Domain '{target_exp.resolved_url}' is recognized as a known safe service.",
                            technical_detail=f"Providers '{threat_res.source}': {threat_res.detail}",
                            weight=0.0,
                            triggered=False
                        ))
                    else:
                        all_signals.append(RiskSignal(
                            category="threat_intel",
                            code="REPUTATION_UNKNOWN",
                            description=f"No prior threat intelligence record found for '{target_exp.resolved_url}'.",
                            technical_detail=f"Providers '{threat_res.source}': UNKNOWN (not evidence of safety)",
                            weight=0.05,
                            triggered=False
                        ))
            except Exception as e:
                logger.error(f"Threat intel batch lookup failed or timed out: {e}")
                degraded = True
                degraded_reasons.append("threat_intel_timeout")

        # 4. Identity Verification Signals
        all_url_strings = [exp.resolved_url for exp in resolved_urls] if resolved_urls else target_urls
        trai_info = None
        try:
            trai_signals, trai_info = await self.trai_provider.verify(
                request.sender_id, request.claimed_organization, all_url_strings
            )
            all_signals.extend(trai_signals)
        except Exception as e:
            logger.error(f"TRAI header verification failed or timed out: {e}")
            try:
                id_task = asyncio.create_task(
                    self.identity_provider.verify(request.sender_id, request.claimed_organization, all_url_strings)
                )
                id_signals = await asyncio.wait_for(id_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
                all_signals.extend(id_signals)
            except Exception as e2:
                logger.error(f"Fallback identity verification failed: {e2}")
                degraded = True
                degraded_reasons.append("identity_verification_timeout")

        # 5. Risk Fusion
        score, level, confidence, reasons, action, should_block, should_report = self.fusion_engine.fuse(
            signals=all_signals,
            has_url=bool(target_urls),
            degraded=degraded
        )

        primary_detected_url = resolved_urls[0].resolved_url if resolved_urls else None

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
            detected_url=primary_detected_url,
            sender=request.sender_id,
            model_version="1.0.0",
            degraded=degraded,
            degraded_reason=",".join(degraded_reasons) if degraded_reasons else None,
            threat_intel=primary_threat_intel_info,
            trai_identity=trai_info
        )

        # Persist analysis
        await self.repo.save(response, request)
        return response
