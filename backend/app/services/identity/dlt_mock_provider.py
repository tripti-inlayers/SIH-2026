import re
from typing import Optional, List
from urllib.parse import urlparse
from app.schemas.common import RiskSignal

DLT_HEADER_REGEX = r"^[A-Z0-9]{2,3}-[A-Z0-9]{6}$"
PHONE_REGEX = r"^\+?\d{10,12}$"
SPECIFIC_BRANDS = ["sbi", "state bank", "hdfc", "icici", "axis", "india post", "irctc", "airtel", "jio"]

class DltMockIdentityProvider:
    async def verify(
        self,
        sender_id: Optional[str],
        claimed_organization: Optional[str],
        urls: List[str]
    ) -> List[RiskSignal]:
        signals: List[RiskSignal] = []
        sender = (sender_id or "").strip().upper()

        is_dlt_header = bool(re.match(DLT_HEADER_REGEX, sender))
        is_phone_number = bool(re.match(PHONE_REGEX, sender)) or (len(sender) == 10 and sender.isdigit())

        # 1. DLT Header Unverified / Verification status
        signals.append(RiskSignal(
            category="identity",
            code="DLT_HEADER_UNVERIFIED",
            description="Sender verification checked against TRAI DLT registration registry (demo data).",
            technical_detail=f"Sender '{sender or 'Unknown'}' is_dlt_header={is_dlt_header}, is_personal_number={is_phone_number} (mock provider)",
            weight=0.05,
            triggered=not is_dlt_header
        ))

        # 2. Sender Organization Mismatch
        has_org_claim = bool(claimed_organization)
        mismatch_triggered = False
        if has_org_claim:
            if is_phone_number or not is_dlt_header:
                mismatch_triggered = True

        signals.append(RiskSignal(
            category="identity",
            code="SENDER_ORGANIZATION_MISMATCH",
            description=f"The sender header '{sender or 'unknown'}' does not match the official registered header for {claimed_organization or 'the claimed organization'}.",
            technical_detail=f"Claimed org '{claimed_organization}' received from personal or unverified sender '{sender}'.",
            weight=0.15,
            triggered=mismatch_triggered
        ))

        # 3. Sender Domain Mismatch
        domain_mismatch = False
        mismatched_domain = ""
        if claimed_organization and urls:
            org_lower = claimed_organization.lower()
            is_specific_brand = any(b in org_lower for b in SPECIFIC_BRANDS)
            if is_specific_brand:
                org_token = re.sub(r"\s+", "", org_lower)
                for u in urls:
                    try:
                        host = urlparse(u).netloc.lower()
                        if org_token not in host and "sbi" not in host and "indiapost" not in host and "gov.in" not in host:
                            domain_mismatch = True
                            mismatched_domain = host
                            break
                    except Exception:
                        pass

        signals.append(RiskSignal(
            category="identity",
            code="SENDER_DOMAIN_MISMATCH",
            description="The destination link domain does not match the sender's claimed organization.",
            technical_detail=f"Claimed org '{claimed_organization}' points to unrelated domain '{mismatched_domain}'",
            weight=0.15,
            triggered=domain_mismatch
        ))

        return signals
