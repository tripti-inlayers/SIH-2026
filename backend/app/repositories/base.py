from typing import Protocol, Optional
from app.schemas.analyze import AnalyzeRequest, RiskResultResponse
from app.schemas.reports import ReportRequest, ReportResponse

class AnalysisRepository(Protocol):
    async def save(self, result: RiskResultResponse, request: AnalyzeRequest) -> None:
        ...
    async def get(self, analysis_id: str) -> Optional[RiskResultResponse]:
        ...

class ReportRepository(Protocol):
    async def save(self, report: ReportResponse) -> None:
        ...
    async def get(self, report_id: str) -> Optional[ReportResponse]:
        ...
