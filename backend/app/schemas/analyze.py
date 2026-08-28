from pydantic import BaseModel, Field
from typing import Optional, List
from app.schemas.common import RiskLevel, CaptureSource, RiskSignal

class ThreatIntelInfo(BaseModel):
    provider: str = "phishdestroy"
    checked: bool = False
    reachable: bool = False
    threat: bool = False
    risk_score: int = 0
    severity: Optional[str] = None
    flags: List[str] = Field(default_factory=list)
    matched_keywords: List[str] = Field(default_factory=list)
    error: Optional[str] = None
    degraded: bool = False
    verdict: str = "UNAVAILABLE"

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
    threat_intel: Optional[ThreatIntelInfo] = None

class UrlAnalyzeRequest(BaseModel):
    url: str

class UrlAnalyzeResponse(BaseModel):
    url: str
    signals: List[RiskSignal]
    url_risk_score: int
    threat_intel: Optional[ThreatIntelInfo] = None

class DiagnosticAnalyzeRequest(BaseModel):
    text: str
    urls: List[str] = Field(default_factory=list)
    sender_id: Optional[str] = None
    mock_webrisk_verdict: Optional[str] = None  # e.g., "SOCIAL_ENGINEERING", "MALWARE", "CLEAN", "UNAVAILABLE", None (live)

class DiagnosticAnalyzeResponse(BaseModel):
    text: str
    url: Optional[str] = None
    webrisk_request_status: str
    webrisk_matched_threat_types: List[str]
    webrisk_normalized_signal: Optional[RiskSignal] = None
    webrisk_contribution_points: int
    ml_score_points: int
    heuristic_score_points: int
    identity_score_points: int
    final_fused_score: int
    final_risk_level: RiskLevel
    degraded: bool
    degraded_reasons: List[str]
