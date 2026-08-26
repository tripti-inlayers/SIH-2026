from fastapi import APIRouter, HTTPException, status
from app.schemas.reports import ReportRequest, ReportResponse
from app.services.report_service import ReportService
from app.core.logging import logger

router = APIRouter()
report_service = ReportService()

@router.post("/reports", response_model=ReportResponse, status_code=status.HTTP_201_CREATED)
async def submit_report(request: ReportRequest):
    logger.info(f"Report submission for analysis_id={request.analysis_id}, threat={request.threat_type}")
    try:
        report = await report_service.submit_report(request)
        return report
    except Exception as e:
        logger.error(f"Error submitting report: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "report_failed", "message": str(e)}
        )

@router.get("/reports/{report_id}", response_model=ReportResponse)
async def get_report(report_id: str):
    report = await report_service.get_report(report_id)
    if not report:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": "not_found", "message": f"Report '{report_id}' not found."}
        )
    return report
