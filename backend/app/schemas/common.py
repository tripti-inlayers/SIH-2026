from enum import Enum
from pydantic import BaseModel

class RiskLevel(str, Enum):
    LOW = "LOW"
    SUSPICIOUS = "SUSPICIOUS"
    HIGH = "HIGH"

class CaptureSource(str, Enum):
    SMS = "SMS"
    REAL_SMS = "REAL_SMS"
    DEMO = "DEMO"
    SHARED = "SHARED"
    MANUAL_INPUT = "MANUAL_INPUT"
    URL_ANALYSIS = "URL_ANALYSIS"

class RiskSignal(BaseModel):
    category: str
    code: str
    description: str
    technical_detail: str
    weight: float
    triggered: bool
