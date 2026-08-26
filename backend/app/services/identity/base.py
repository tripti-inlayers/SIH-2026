from typing import Protocol, Optional, List
from app.schemas.common import RiskSignal

class IdentityProvider(Protocol):
    async def verify(
        self,
        sender_id: Optional[str],
        claimed_organization: Optional[str],
        urls: List[str]
    ) -> List[RiskSignal]:
        ...
