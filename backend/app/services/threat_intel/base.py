from typing import Protocol, List, Optional
from enum import Enum
from pydantic import BaseModel, Field

class ThreatIntelVerdict(str, Enum):
    CHECKED_CLEAN = "CHECKED_CLEAN"
    CHECKED_THREAT = "CHECKED_THREAT"
    UNAVAILABLE = "UNAVAILABLE"

class ThreatIntelResult(BaseModel):
    provider: str = "phishdestroy"
    checked: bool = False
    reachable: bool = False
    threat: bool = False
    riskScore: int = 0
    severity: Optional[str] = None
    flags: List[str] = Field(default_factory=list)
    matchedKeywords: List[str] = Field(default_factory=list)
    error: Optional[str] = None
    degraded: bool = False
    verdict: ThreatIntelVerdict = ThreatIntelVerdict.UNAVAILABLE

class ThreatIntelProvider(Protocol):
    async def lookup(self, url: str) -> ThreatIntelResult:
        ...
    async def probe_health(self) -> dict:
        ...
