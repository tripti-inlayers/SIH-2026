from fastapi import APIRouter, HTTPException
from models.schemas import AnalyzeRequest, AnalyzeResponse, ReportRequest
from services.orchestrator import OrchestratorService
import hashlib
from database import log_report

router = APIRouter()
orchestrator = OrchestratorService()

@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest):
    try:
        response = await orchestrator.analyze_content(request.content, request.source, request.sender)
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Internal Analysis Error: {str(e)}")

@router.post("/report")
async def report(request: ReportRequest):
    try:
        # Compute SHA256 content hash for privacy protection
        content_hash = hashlib.sha256(request.content.encode('utf-8')).hexdigest()
        # Convert signals to dictionary lists for DB storage
        serialized_signals = [sig.model_dump() for sig in request.signals]
        # Log insertion
        log_report(
            content_hash=content_hash,
            decision=request.decision,
            risk_score=request.risk_score,
            signals=serialized_signals,
            report_type=request.report_type
        )
        return {"status": "success", "message": "Report logged successfully"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Reporting failed: {str(e)}")
