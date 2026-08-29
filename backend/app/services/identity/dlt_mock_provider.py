from typing import Optional, List, Tuple
from app.schemas.common import RiskSignal
from app.schemas.analyze import TraiIdentityInfo
from app.services.identity.trai_registry import TraiHeaderRegistryProvider

class DltMockIdentityProvider:
    def __init__(self):
        self._provider = TraiHeaderRegistryProvider()

    async def verify(
        self,
        sender_id: Optional[str],
        claimed_organization: Optional[str],
        urls: List[str]
    ) -> List[RiskSignal]:
        signals, _ = await self._provider.verify(sender_id, claimed_organization, urls)
        return signals

    async def verify_full(
        self,
        sender_id: Optional[str],
        claimed_organization: Optional[str],
        urls: List[str]
    ) -> Tuple[List[RiskSignal], TraiIdentityInfo]:
        return await self._provider.verify(sender_id, claimed_organization, urls)
