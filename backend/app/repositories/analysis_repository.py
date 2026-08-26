from app.schemas.analyze import AnalyzeRequest, RiskResultResponse
from app.repositories.in_memory_repository import in_memory_analysis_repo
from app.db.session import AsyncSessionLocal
from app.db.models import AnalysisModel
from app.core.logging import logger
from typing import Optional
from sqlalchemy import select

class DefaultAnalysisRepository:
    async def save(self, result: RiskResultResponse, request: AnalyzeRequest) -> None:
        if AsyncSessionLocal is not None:
            try:
                async with AsyncSessionLocal() as session:
                    model = AnalysisModel(
                        analysis_id=result.analysis_id,
                        risk_score=result.risk_score,
                        risk_level=result.risk_level.value,
                        confidence=result.confidence,
                        detected_url=result.detected_url,
                        sender=result.sender,
                        reasons=[r for r in result.reasons],
                        signals=[s.model_dump() for s in result.signals],
                        should_block=result.should_block,
                        should_report=result.should_report,
                        degraded=result.degraded,
                        degraded_reason=result.degraded_reason,
                        model_version=result.model_version,
                        source=request.source.value
                    )
                    session.add(model)
                    await session.commit()
                    return
            except Exception as e:
                logger.error(f"Failed to persist analysis to DB ({e}); falling back to in-memory save.")
        
        await in_memory_analysis_repo.save(result, request)

    async def get(self, analysis_id: str) -> Optional[RiskResultResponse]:
        if AsyncSessionLocal is not None:
            try:
                async with AsyncSessionLocal() as session:
                    stmt = select(AnalysisModel).where(AnalysisModel.analysis_id == analysis_id)
                    res = await session.execute(stmt)
                    row = res.scalar_one_or_none()
                    if row:
                        return RiskResultResponse(
                            analysis_id=row.analysis_id,
                            risk_score=row.risk_score,
                            risk_level=row.risk_level,
                            confidence=row.confidence,
                            reasons=row.reasons,
                            signals=row.signals,
                            recommended_action="Do not open this link" if row.should_block else "Stay safe",
                            should_block=row.should_block,
                            should_report=row.should_report,
                            detected_url=row.detected_url,
                            sender=row.sender,
                            model_version=row.model_version,
                            degraded=row.degraded,
                            degraded_reason=row.degraded_reason
                        )
            except Exception as e:
                logger.error(f"Failed to fetch analysis from DB ({e}); falling back to in-memory store.")
                
        return await in_memory_analysis_repo.get(analysis_id)

get_analysis_repository = DefaultAnalysisRepository
