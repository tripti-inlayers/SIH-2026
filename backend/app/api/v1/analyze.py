from fastapi import APIRouter, HTTPException, status
from app.schemas.analyze import AnalyzeRequest, RiskResultResponse, UrlAnalyzeRequest, UrlAnalyzeResponse
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
        
        # Also query Threat Intel / Web Risk
        web_risk_data = None
        try:
            threat_res = await orchestrator.threat_intel_provider.lookup(request.url)
            web_risk_data = {
                "available": threat_res.available,
                "matched": threat_res.matched,
                "threat_types": threat_res.threat_types,
                "detail": threat_res.detail
            }
            if threat_res.matched:
                signals.append(RiskSignal(
                    category="threat_intel",
                    code="REPUTATION_MALICIOUS",
                    description=threat_res.detail,
                    technical_detail=f"Provider '{threat_res.source}': {threat_res.detail}",
                    weight=0.80,
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
            web_risk=web_risk_data
        )
    except Exception as e:
        logger.error(f"Error during URL analysis: {e}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "invalid_url", "message": str(e)}
        )
