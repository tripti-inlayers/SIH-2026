from typing import Protocol, List, Optional
from enum import Enum
from pydantic import BaseModel, Field

class ThreatIntelVerdict(str, Enum):
    KNOWN_MALICIOUS = "KNOWN_MALICIOUS"
    UNKNOWN = "UNKNOWN"
    KNOWN_SAFE = "KNOWN_SAFE"

class ThreatIntelResult(BaseModel):
    verdict: ThreatIntelVerdict
    source: str
    detail: str
    available: bool = True
    matched: bool = False
    threat_types: List[str] = Field(default_factory=list)
    expire_time: Optional[str] = None
    error: Optional[str] = None

class ThreatIntelProvider(Protocol):
    async def lookup(self, url: str) -> ThreatIntelResult:
        ...
