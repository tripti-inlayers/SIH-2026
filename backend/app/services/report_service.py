import uuid
import time
from app.schemas.reports import ReportRequest, ReportResponse
from app.repositories.report_repository import get_report_repository

class ReportService:
    def __init__(self):
        self.repo = get_report_repository()

    async def submit_report(self, request: ReportRequest) -> ReportResponse:
        report_id = f"RPT-{uuid.uuid4().hex[:8].upper()}"
        now_ms = int(time.time() * 1000)
        
        response = ReportResponse(
            report_id=report_id,
            timestamp_epoch_millis=now_ms,
            threat_type=request.threat_type,
            url_or_domain=request.url_or_domain,
            risk_score=request.risk_score,
            risk_level=request.risk_level,
            evidence_summary=request.evidence_summary,
            submitted=True,
            integration_note="Proposed Reporting Integration — demonstration only."
        )
        
        await self.repo.save(response)
        return response

    async def get_report(self, report_id: str) -> ReportResponse | None:
        return await self.repo.get(report_id)
