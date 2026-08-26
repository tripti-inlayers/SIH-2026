import re
from typing import Optional, List
from app.schemas.common import RiskSignal
from app.services.message_analysis_patterns import (
    URGENCY_PATTERNS, CREDENTIAL_PATTERNS, FINANCIAL_PATTERNS,
    REWARD_PATTERNS, THREAT_PATTERNS, GENERIC_SOCIAL_ENG_PATTERNS, ORG_NAMES
)

class MessageAnalysisService:
    def analyze(self, text: str, claimed_organization: Optional[str] = None) -> List[RiskSignal]:
        signals: List[RiskSignal] = []
        lower_text = text.lower()

        # 1. Urgency Language
        urgency_match = any(re.search(p, lower_text) for p in URGENCY_PATTERNS)
        signals.append(RiskSignal(
            category="message",
            code="URGENCY_LANGUAGE",
            description="The message uses urgent language pressuring quick action.",
            technical_detail=f"Pattern matched urgency phrases in message content.",
            weight=0.15,
            triggered=urgency_match
        ))

        # 2. Impersonation Claim
        org_found = claimed_organization
        if not org_found:
            for org in ORG_NAMES:
                if org in lower_text:
                    org_found = org
                    break
        signals.append(RiskSignal(
            category="message",
            code="IMPERSONATION_CLAIM",
            description=f"Message claims to represent an organization ({org_found or 'unnamed entity'}).",
            technical_detail=f"Claimed/detected organization: {org_found or 'None'}",
            weight=0.05,
            triggered=bool(org_found)
        ))

        # 3. Credential Request
        cred_match = any(re.search(p, lower_text) for p in CREDENTIAL_PATTERNS)
        signals.append(RiskSignal(
            category="message",
            code="CREDENTIAL_REQUEST",
            description="The message asks for sensitive credentials such as PIN, password, or OTP.",
            technical_detail="Pattern matched credential request indicators.",
            weight=0.20,
            triggered=cred_match
        ))

        # 4. Financial Request
        fin_match = any(re.search(p, lower_text) for p in FINANCIAL_PATTERNS)
        signals.append(RiskSignal(
            category="message",
            code="FINANCIAL_REQUEST",
            description="The message involves sensitive financial details, bank accounts, or payments.",
            technical_detail="Pattern matched financial request indicators.",
            weight=0.15,
            triggered=fin_match
        ))

        # 5. Reward Bait
        reward_match = any(re.search(p, lower_text) for p in REWARD_PATTERNS)
        signals.append(RiskSignal(
            category="message",
            code="REWARD_BAIT",
            description="The message promises unrealistic rewards, prizes, or lotteries.",
            technical_detail="Pattern matched reward bait phrases.",
            weight=0.10,
            triggered=reward_match
        ))

        # 6. Threat Language
        threat_match = any(re.search(p, lower_text) for p in THREAT_PATTERNS)
        signals.append(RiskSignal(
            category="message",
            code="THREAT_LANGUAGE",
            description="The message uses intimidating threat language such as account closure or penalties.",
            technical_detail="Pattern matched threat/intimidation indicators.",
            weight=0.10,
            triggered=threat_match
        ))

        # 7. Generic Social Engineering
        social_match = any(re.search(p, lower_text) for p in GENERIC_SOCIAL_ENG_PATTERNS)
        signals.append(RiskSignal(
            category="message",
            code="GENERIC_SOCIAL_ENGINEERING",
            description="Message matches common scam template structures.",
            technical_detail="Pattern matched generic social engineering cues.",
            weight=0.05,
            triggered=social_match
        ))

        return signals
