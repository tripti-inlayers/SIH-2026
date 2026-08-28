from pydantic import BaseModel
from typing import Optional, List
from app.schemas.common import RiskLevel

class ReportRequest(BaseModel):
    analysis_id: str
    threat_type: str
    url_or_domain: Optional[str] = None
    risk_score: int
    risk_level: RiskLevel
    evidence_summary: List[str]

class ReportResponse(BaseModel):
    report_id: str
    timestamp_epoch_millis: int
    threat_type: str
    url_or_domain: Optional[str] = None
    risk_score: int
    risk_level: RiskLevel
    evidence_summary: List[str]
    submitted: bool
    integration_note: str = "Proposed Reporting Integration — demonstration only."

class HealthResponse(BaseModel):
    status: str
    database: str
    threat_intel_provider: str
    identity_provider: str
    version: str
    ml_service: Optional[dict] = None
    threat_intel: Optional[dict] = None
