from pydantic import BaseModel, Field
from typing import List, Optional

class AnalyzeRequest(BaseModel):
    content: str
    source: str = "manual"
    sender: Optional[str] = None

class Signal(BaseModel):
    source: str
    description: str
    confidence: float
    weight: float

class AnalyzeResponse(BaseModel):
    risk_score: float = Field(..., ge=0, le=100)
    risk_level: str  # "LOW" | "MEDIUM" | "HIGH"
    decision: str     # "ALLOW" | "WARN" | "BLOCK"
    signals: List[Signal]
    explanation: str
    partial_analysis: bool = False

class ReportRequest(BaseModel):
    content: str
    decision: str
    risk_score: float
    signals: List[Signal]
    report_type: str  # "spam_report" | "bypass_report"
