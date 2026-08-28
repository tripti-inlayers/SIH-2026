from fastapi import APIRouter, HTTPException, status
from app.schemas.analyze import (
    AnalyzeRequest,
    RiskResultResponse,
    UrlAnalyzeRequest,
    UrlAnalyzeResponse,
    ThreatIntelInfo,
    DiagnosticAnalyzeRequest,
    DiagnosticAnalyzeResponse
)
from app.schemas.common import RiskSignal, RiskLevel
from app.services.orchestrator import AnalysisOrchestrator, extract_and_deduplicate_urls
from app.services.threat_intel.base import ThreatIntelResult, ThreatIntelVerdict
from app.services.url_analysis import UrlAnalysisService
from app.services.message_analysis import MessageAnalysisService
from app.services.ml_analysis import MlAnalysisService
from app.services.identity.dlt_mock_provider import DltMockIdentityProvider
from app.services.risk_fusion import RiskFusionEngine
from app.core.logging import logger, redact_text
import asyncio

router = APIRouter()
orchestrator = AnalysisOrchestrator()
url_analyzer = UrlAnalysisService()
message_analyzer = MessageAnalysisService()
ml_analyzer = MlAnalysisService()
identity_provider = DltMockIdentityProvider()
fusion_engine = RiskFusionEngine()

@router.post("/analyze", response_model=RiskResultResponse)
async def analyze_message(request: AnalyzeRequest):
    logger.info(f"Analyze request received: msg_id={request.message_id}, source={request.source}, text={redact_text(request.text)}")
    try:
        result = await orchestrator.analyze(request)
        return result
    except Exception as e:
        logger.error(f"Error during analysis: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "analysis_failed", "message": str(e)}
        )

@router.post("/analyze/url", response_model=UrlAnalyzeResponse)
async def analyze_url_only(request: UrlAnalyzeRequest):
    logger.info(f"URL analysis request received for URL length {len(request.url)}")
    try:
        signals = url_analyzer.analyze(request.url)
        
        # Also query Threat Intel / PhishDestroy
        threat_intel_info = None
        try:
            threat_res = await orchestrator.threat_intel_provider.lookup(request.url)
            threat_intel_info = ThreatIntelInfo(
                provider="phishdestroy",
                checked=True,
                reachable=threat_res.reachable,
                threat=threat_res.threat,
                risk_score=threat_res.riskScore,
                severity=threat_res.severity,
                flags=threat_res.flags,
                matched_keywords=threat_res.matchedKeywords,
                error=threat_res.error,
                degraded=threat_res.degraded,
                verdict=threat_res.verdict.value
            )
            if threat_res.threat:
                signals.append(RiskSignal(
                    category="threat_intel",
                    code="REPUTATION_MALICIOUS",
                    description=f"PhishDestroy flagged this domain as threat (score: {threat_res.riskScore})",
                    technical_detail=f"Provider 'phishdestroy': threat={threat_res.threat}, flags={threat_res.flags}",
                    weight=threat_res.riskScore / 100.0,
                    triggered=True
                ))
        except Exception as e:
            logger.error(f"Threat intel check in analyze_url_only failed: {e}")

        triggered_score = sum(s.weight for s in signals if s.triggered)
        url_score = min(100, max(0, int(round(triggered_score * 100))))
        return UrlAnalyzeResponse(
            url=request.url,
            signals=signals,
            url_risk_score=url_score,
            threat_intel=threat_intel_info
        )
    except Exception as e:
        logger.error(f"Error during URL analysis: {e}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "invalid_url", "message": str(e)}
        )

@router.post("/analyze/diagnostics", response_model=DiagnosticAnalyzeResponse)
async def analyze_diagnostics(request: DiagnosticAnalyzeRequest):
    """
    Diagnostic evaluation endpoint breaking down PhishDestroy, ML, and heuristic contribution scores.
    Supports mock_webrisk_verdict for deterministic testing without secret leakage.
    """
    urls = extract_and_deduplicate_urls(request.urls, request.text)
    primary_url = urls[0] if urls else None

    all_signals: list[RiskSignal] = []
    degraded = False
    degraded_reasons: list[str] = []

    # 1. Message & URL heuristic signals
    msg_signals = message_analyzer.analyze(request.text)
    all_signals.extend(msg_signals)

    url_signals = []
    for u in urls:
        url_signals.extend(url_analyzer.analyze(u))
    all_signals.extend(url_signals)

    # 2. ML Analysis
    try:
        ml_signal = await ml_analyzer.analyze(request.text)
        if ml_signal:
            all_signals.append(ml_signal)
    except Exception as e:
        degraded = True
        degraded_reasons.append("ml_service_unavailable")

    # 3. Threat Intel / PhishDestroy
    webrisk_status = "NOT_APPLICABLE"
    matched_types = []
    normalized_signal = None
    webrisk_points = 0

    if urls:
        if request.mock_webrisk_verdict is not None:
            v = request.mock_webrisk_verdict.upper()
            if v in ("SOCIAL_ENGINEERING", "MALWARE"):
                webrisk_status = "MATCHED"
                matched_types = [v]
                normalized_signal = RiskSignal(
                    category="threat_intel",
                    code=f"WEBRISK_{v}",
                    description=f"PhishDestroy identified this URL as {v}.",
                    technical_detail=f"Mocked Threat Intel verdict: {v}",
                    weight=0.80,
                    triggered=True
                )
                webrisk_points = 80
                all_signals.append(normalized_signal)
            elif v == "UNAVAILABLE":
                webrisk_status = "UNAVAILABLE"
                degraded = True
                degraded_reasons.append("threat_intel_unavailable")
                normalized_signal = RiskSignal(
                    category="threat_intel",
                    code="WEBRISK_UNAVAILABLE",
                    description="Threat intelligence lookup unavailable.",
                    technical_detail="Mocked Threat Intel unavailable",
                    weight=0.0,
                    triggered=False
                )
                all_signals.append(normalized_signal)
            else:
                webrisk_status = "CLEAN"
                normalized_signal = RiskSignal(
                    category="threat_intel",
                    code="REPUTATION_UNKNOWN",
                    description="PhishDestroy: No matching threat found.",
                    technical_detail="Mocked Threat Intel clean",
                    weight=0.0,
                    triggered=False
                )
                all_signals.append(normalized_signal)
        else:
            # Live lookup
            try:
                res = await orchestrator.threat_intel_provider.lookup(urls[0])
                if not res.reachable:
                    webrisk_status = "UNAVAILABLE"
                    degraded = True
                    degraded_reasons.append("threat_intel_unavailable")
                elif res.threat:
                    webrisk_status = "MATCHED"
                    matched_types = res.flags or ["threat"]
                    normalized_signal = RiskSignal(
                        category="threat_intel",
                        code="REPUTATION_MALICIOUS",
                        description=f"PhishDestroy flagged threat with score {res.riskScore}",
                        technical_detail=f"Live PhishDestroy: threat={res.threat}, flags={res.flags}",
                        weight=res.riskScore / 100.0,
                        triggered=True
                    )
                    webrisk_points = res.riskScore
                    all_signals.append(normalized_signal)
                else:
                    webrisk_status = "CLEAN"
            except Exception as e:
                webrisk_status = "ERROR"
                degraded = True
                degraded_reasons.append(f"threat_intel_error: {str(e)}")

    # 4. Identity Verification
    id_signals = await identity_provider.verify(request.sender_id, None, urls)
    all_signals.extend(id_signals)

    # 5. Component breakdown calculations
    heuristic_points = int(round(sum(s.weight for s in all_signals if s.category in ("message", "url") and s.triggered) * 100))
    ml_points = int(round(sum(s.weight for s in all_signals if s.category == "ml_model" and s.triggered) * 100))
    id_points = int(round(sum(s.weight for s in all_signals if s.category == "identity" and s.triggered) * 100))

    # 6. Risk Fusion
    pd_score_val = webrisk_points
    pd_threat_val = (webrisk_status == "MATCHED")
    score, level, confidence, reasons, action, should_block, should_report = fusion_engine.fuse(
        signals=all_signals,
        has_url=bool(urls),
        degraded=degraded,
        phishdestroy_score=pd_score_val,
        phishdestroy_threat=pd_threat_val
    )

    return DiagnosticAnalyzeResponse(
        text=request.text,
        url=primary_url,
        webrisk_request_status=webrisk_status,
        webrisk_matched_threat_types=matched_types,
        webrisk_normalized_signal=normalized_signal,
        webrisk_contribution_points=webrisk_points,
        ml_score_points=ml_points,
        heuristic_score_points=heuristic_points,
        identity_score_points=id_points,
        final_fused_score=score,
        final_risk_level=level,
        degraded=degraded,
        degraded_reasons=degraded_reasons
    )
