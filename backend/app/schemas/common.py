from enum import Enum
from pydantic import BaseModel

class RiskLevel(str, Enum):
    LOW = "LOW"
    SUSPICIOUS = "SUSPICIOUS"
    HIGH = "HIGH"

class CaptureSource(str, Enum):
    SMS = "SMS"
    DEMO = "DEMO"
    SHARED = "SHARED"

class RiskSignal(BaseModel):
    category: str
    code: str
    description: str
    technical_detail: str
    weight: float
    triggered: bool
