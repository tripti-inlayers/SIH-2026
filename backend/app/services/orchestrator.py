import asyncio
import uuid
from typing import List, Optional
from app.schemas.analyze import AnalyzeRequest, RiskResultResponse
from app.schemas.common import RiskSignal
from app.services.message_analysis import MessageAnalysisService
from app.services.url_analysis import UrlAnalysisService
from app.services.threat_intel.base import ThreatIntelVerdict
from app.services.threat_intel.mock_provider import MockThreatIntelProvider
from app.services.threat_intel.rdap_provider import RdapThreatIntelProvider
from app.services.identity.dlt_mock_provider import DltMockIdentityProvider
from app.services.risk_fusion import RiskFusionEngine
from app.services.ml_analysis import MlAnalysisService
from app.repositories.analysis_repository import get_analysis_repository
from app.config import settings
from app.core.logging import logger

class AnalysisOrchestrator:
    def __init__(self):
        self.message_service = MessageAnalysisService()
        self.url_service = UrlAnalysisService()
        self.ml_service = MlAnalysisService()
        
        if settings.THREAT_INTEL_PROVIDER == "rdap":
            self.threat_intel_provider = RdapThreatIntelProvider()
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

        # 1.5. ML Model Signals
        try:
            ml_task = asyncio.create_task(self.ml_service.analyze(request.text))
            ml_signal = await asyncio.wait_for(ml_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
            if ml_signal:
                all_signals.append(ml_signal)
        except Exception as e:
            logger.error(f"ML analysis failed or timed out: {e}")
            degraded = True
            degraded_reasons.append("ml_analysis_timeout")

        # 2. URL Signals
        primary_url = request.urls[0] if request.urls else None
        if request.urls:
            try:
                url_task = asyncio.create_task(
                    asyncio.to_thread(self.url_service.analyze, primary_url)
                )
                url_signals = await asyncio.wait_for(url_task, timeout=settings.REQUEST_TIMEOUT_SECONDS)
                all_signals.extend(url_signals)
            except Exception as e:
                logger.error(f"URL analysis failed or timed out: {e}")
                degraded = True
                degraded_reasons.append("url_analysis_timeout")

        # 3. Threat Intel Signals
        if primary_url:
            try:
                threat_intel_res = await asyncio.wait_for(
                    self.threat_intel_provider.lookup(primary_url),
                    timeout=settings.REQUEST_TIMEOUT_SECONDS
                )
                verdict = threat_intel_res.verdict
                if verdict == ThreatIntelVerdict.KNOWN_MALICIOUS:
                    all_signals.append(RiskSignal(
                        category="threat_intel",
                        code="REPUTATION_MALICIOUS",
                        description="This domain is flagged as known malicious by threat intelligence.",
                        technical_detail=f"Provider '{threat_intel_res.source}': {threat_intel_res.detail}",
                        weight=0.35,
                        triggered=True
                    ))
                elif verdict == ThreatIntelVerdict.KNOWN_SAFE:
                    all_signals.append(RiskSignal(
                        category="threat_intel",
                        code="REPUTATION_SAFE",
                        description="This domain is recognized as a known safe service.",
                        technical_detail=f"Provider '{threat_intel_res.source}': {threat_intel_res.detail}",
                        weight=0.0,
                        triggered=False
                    ))
                else:
                    all_signals.append(RiskSignal(
                        category="threat_intel",
                        code="REPUTATION_UNKNOWN",
                        description="No prior threat intelligence record found for this domain.",
                        technical_detail=f"Provider '{threat_intel_res.source}': UNKNOWN (not evidence of safety)",
                        weight=0.05,
                        triggered=False
                    ))
            except Exception as e:
                logger.error(f"Threat intel lookup failed or timed out: {e}")
                degraded = True
                degraded_reasons.append("threat_intel_timeout")

        # 4. Identity Verification Signals
        try:
            id_task = asyncio.create_task(
                self.identity_provider.verify(request.sender_id, request.claimed_organization, request.urls)
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
            has_url=bool(request.urls),
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
