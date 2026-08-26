from typing import Protocol
from enum import Enum
from pydantic import BaseModel

class ThreatIntelVerdict(str, Enum):
    KNOWN_MALICIOUS = "KNOWN_MALICIOUS"
    UNKNOWN = "UNKNOWN"
    KNOWN_SAFE = "KNOWN_SAFE"

class ThreatIntelResult(BaseModel):
    verdict: ThreatIntelVerdict
    source: str
    detail: str

class ThreatIntelProvider(Protocol):
    async def lookup(self, url: str) -> ThreatIntelResult:
        ...
