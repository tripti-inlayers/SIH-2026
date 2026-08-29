import re
from fastapi import APIRouter, HTTPException, status
from app.schemas.analyze import (
    AnalyzeRequest,
    RiskResultResponse,
    UrlAnalyzeRequest,
    UrlAnalyzeResponse,
    DiagnosticAnalyzeRequest,
    DiagnosticAnalyzeResponse,
    ThreatIntelInfo
)
from app.schemas.common import RiskSignal, RiskLevel
from app.services.orchestrator import AnalysisOrchestrator
from app.services.url_analysis import UrlAnalysisService
from app.core.logging import logger, redact_text

router = APIRouter()
orchestrator = AnalysisOrchestrator()
url_analyzer = UrlAnalysisService()

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
        triggered_score = sum(s.weight for s in signals if s.triggered)
        url_score = min(100, max(0, int(round(triggered_score * 100))))
        return UrlAnalyzeResponse(
            url=request.url,
            signals=signals,
            url_risk_score=url_score
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
    Diagnostic evaluation endpoint breaking down Threat Intel, ML, heuristic, and identity contribution scores.
    Supports mock_webrisk_verdict for deterministic testing.
    """
    target_urls = list(request.urls or [])
    url_pattern = r"(?:https?://|cutt\.ly/|bit\.ly/|tinyurl\.com/|t\.co/|(?:[a-zA-Z0-9-]+\.)+(?:com|ly|in|org|net|xyz|tk|top|io|co|gov|edu)/)[^\s]+"
    for match in re.findall(url_pattern, request.text, re.IGNORECASE):
        normalized = match if match.lower().startswith(("http://", "https://")) else f"https://{match}"
        if normalized not in target_urls:
            target_urls.append(normalized)

    primary_url = target_urls[0] if target_urls else None
    all_signals: list[RiskSignal] = []
    degraded = False
    degraded_reasons: list[str] = []

    # 1. Message NLP Signals
    msg_signals = orchestrator.message_service.analyze(request.text)
    all_signals.extend(msg_signals)

    # 2. URL Heuristic Signals
    for u in target_urls:
        all_signals.extend(orchestrator.url_service.analyze(u))

    # 3. ML Model Signals
    try:
        ml_signal = await orchestrator.ml_service.analyze(request.text)
        if ml_signal:
            all_signals.append(ml_signal)
    except Exception as e:
        logger.error(f"ML analysis failed in diagnostics: {e}")
        degraded = True
        degraded_reasons.append("ml_analysis_timeout")

    # 4. Threat Intel / Web Risk
    webrisk_status = "NOT_APPLICABLE"
    matched_types: list[str] = []
    normalized_signal: RiskSignal | None = None
    webrisk_points = 0

    if target_urls:
        if request.mock_webrisk_verdict is not None:
            v = request.mock_webrisk_verdict.upper()
            if v in ("SOCIAL_ENGINEERING", "MALWARE"):
                webrisk_status = "MATCHED"
                matched_types = [v]
                normalized_signal = RiskSignal(
                    category="threat_intel",
                    code=f"WEBRISK_{v}",
                    description=f"Threat intelligence identified this URL as {v}.",
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
                    description="Threat intelligence: Clean / no known threat match.",
                    technical_detail="Mocked Threat Intel clean",
                    weight=0.0,
                    triggered=False
                )
                all_signals.append(normalized_signal)
        else:
            try:
                intel_res = await orchestrator.threat_intel_provider.lookup(target_urls[0])
                if intel_res.verdict.value == "KNOWN_MALICIOUS":
                    webrisk_status = "MATCHED"
                    matched_types = [intel_res.source]
                    webrisk_points = 85
                    all_signals.append(RiskSignal(
                        category="threat_intel",
                        code="REPUTATION_MALICIOUS",
                        description=f"Domain flagged as malicious by {intel_res.source}",
                        technical_detail=intel_res.detail,
                        weight=0.85,
                        triggered=True
                    ))
                elif intel_res.verdict.value == "KNOWN_SAFE":
                    webrisk_status = "CLEAN"
                else:
                    webrisk_status = "UNKNOWN"
            except Exception as e:
                webrisk_status = "ERROR"
                degraded = True
                degraded_reasons.append(f"threat_intel_error: {str(e)}")

    # 5. Identity Verification Signals
    try:
        trai_signals, _ = await orchestrator.trai_provider.verify(request.sender_id, None, target_urls)
        all_signals.extend(trai_signals)
    except Exception:
        id_signals = await orchestrator.identity_provider.verify(request.sender_id, None, target_urls)
        all_signals.extend(id_signals)

    # 6. Component breakdown calculations
    heuristic_points = int(round(sum(s.weight for s in all_signals if s.category in ("message", "url") and s.triggered) * 100))
    ml_points = int(round(sum(s.weight for s in all_signals if s.category == "ml_model" and s.triggered) * 100))
    id_points = int(round(sum(s.weight for s in all_signals if s.category == "identity" and s.triggered) * 100))

    # 7. Risk Fusion
    score, level, confidence, reasons, action, should_block, should_report = orchestrator.fusion_engine.fuse(
        signals=all_signals,
        has_url=bool(target_urls),
        degraded=degraded
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
