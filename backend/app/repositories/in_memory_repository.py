from typing import Optional, Dict
from app.schemas.analyze import AnalyzeRequest, RiskResultResponse
from app.schemas.reports import ReportResponse

class InMemoryAnalysisRepository:
    def __init__(self):
        self._store: Dict[str, RiskResultResponse] = {}

    async def save(self, result: RiskResultResponse, request: AnalyzeRequest) -> None:
        self._store[result.analysis_id] = result

    async def get(self, analysis_id: str) -> Optional[RiskResultResponse]:
        return self._store.get(analysis_id)

class InMemoryReportRepository:
    def __init__(self):
        self._store: Dict[str, ReportResponse] = {}

    async def save(self, report: ReportResponse) -> None:
        self._store[report.report_id] = report

    async def get(self, report_id: str) -> Optional[ReportResponse]:
        return self._store.get(report_id)

# Singleton instances for fallback
in_memory_analysis_repo = InMemoryAnalysisRepository()
in_memory_report_repo = InMemoryReportRepository()
