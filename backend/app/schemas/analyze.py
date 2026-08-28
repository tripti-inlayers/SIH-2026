from pydantic import BaseModel, Field
from typing import Optional, List
from app.schemas.common import RiskLevel, CaptureSource, RiskSignal

class AnalyzeRequest(BaseModel):
    message_id: str
    text: str = Field(..., max_length=5000)
    urls: List[str] = Field(default_factory=list, max_length=10)
    sender_id: Optional[str] = None
    claimed_organization: Optional[str] = None
    language: Optional[str] = None
    timestamp_epoch_millis: int
    source: CaptureSource

class RiskResultResponse(BaseModel):
    analysis_id: str
    risk_score: int = Field(..., ge=0, le=100)
    risk_level: RiskLevel
    confidence: float = Field(..., ge=0.0, le=1.0)
    reasons: List[str]
    signals: List[RiskSignal]
    recommended_action: str
    should_block: bool
    should_report: bool
    detected_url: Optional[str] = None
    sender: Optional[str] = None
    model_version: str = "1.0.0"
    degraded: bool = False
    degraded_reason: Optional[str] = None

class UrlAnalyzeRequest(BaseModel):
    url: str

class UrlAnalyzeResponse(BaseModel):
    url: str
    signals: List[RiskSignal]
    url_risk_score: int
    web_risk: Optional[dict] = None
