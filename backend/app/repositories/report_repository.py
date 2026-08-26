from app.schemas.reports import ReportResponse
from app.repositories.in_memory_repository import in_memory_report_repo
from app.db.session import AsyncSessionLocal
from app.db.models import ReportModel
from app.core.logging import logger
from typing import Optional
from sqlalchemy import select

class DefaultReportRepository:
    async def save(self, report: ReportResponse) -> None:
        if AsyncSessionLocal is not None:
            try:
                async with AsyncSessionLocal() as session:
                    model = ReportModel(
                        report_id=report.report_id,
                        analysis_id=report.report_id,
                        threat_type=report.threat_type,
                        url_or_domain=report.url_or_domain,
                        risk_score=report.risk_score,
                        risk_level=report.risk_level.value,
                        evidence_summary=report.evidence_summary,
                        submitted=report.submitted
                    )
                    session.add(model)
                    await session.commit()
                    return
            except Exception as e:
                logger.error(f"Failed to persist report to DB ({e}); falling back to in-memory save.")
        
        await in_memory_report_repo.save(report)

    async def get(self, report_id: str) -> Optional[ReportResponse]:
        if AsyncSessionLocal is not None:
            try:
                async with AsyncSessionLocal() as session:
                    stmt = select(ReportModel).where(ReportModel.report_id == report_id)
                    res = await session.execute(stmt)
                    row = res.scalar_one_or_none()
                    if row:
                        return ReportResponse(
                            report_id=row.report_id,
                            timestamp_epoch_millis=int(row.created_at.timestamp() * 1000) if row.created_at else 0,
                            threat_type=row.threat_type,
                            url_or_domain=row.url_or_domain,
                            risk_score=row.risk_score,
                            risk_level=row.risk_level,
                            evidence_summary=row.evidence_summary,
                            submitted=row.submitted,
                            integration_note="Proposed Reporting Integration — demonstration only."
                        )
            except Exception as e:
                logger.error(f"Failed to fetch report from DB ({e}); falling back to in-memory store.")
                
        return await in_memory_report_repo.get(report_id)

get_report_repository = DefaultReportRepository
