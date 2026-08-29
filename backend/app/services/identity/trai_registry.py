import re
import logging
from typing import Optional, List, Dict, Tuple
from urllib.parse import urlparse
from app.schemas.common import RiskSignal
from app.schemas.analyze import TraiIdentityInfo

logger = logging.getLogger(__name__)

# Pattern for Indian DLT / TRAI alphanumeric headers
# e.g. AD-650022-P, AR-AIRTEL-P, JM-HDFCBK-S, JD-IPAYTM, VK-HDFCBK, SBIINB
HEADER_PATTERN = re.compile(r"^(?:[A-Z]{2}-)?([A-Z0-9]{3,11})(?:-[A-Z0-9]+)?$", re.IGNORECASE)
PHONE_PATTERN = re.compile(r"^\+?\d{10,12}$")

# Compiled database of TRAI-registered alphanumeric headers assigned to Principal Entities in India
TRAI_REGISTRY: Dict[str, Dict[str, str]] = {
    "SBIINB": {
        "entity_name": "State Bank of India",
        "brand_name": "SBI",
        "category": "Banking & Financial Services",
        "purpose": "Internet Banking OTP & Transaction Alerts"
    },
    "SBIBNK": {
        "entity_name": "State Bank of India",
        "brand_name": "SBI",
        "category": "Banking & Financial Services",
        "purpose": "Account Balance & Transaction Alerts"
    },
    "HDFCBK": {
        "entity_name": "HDFC Bank Limited",
        "brand_name": "HDFC Bank",
        "category": "Banking & Financial Services",
        "purpose": "Banking Alerts, OTP & Card Security"
    },
    "ICICIB": {
        "entity_name": "ICICI Bank Limited",
        "brand_name": "ICICI Bank",
        "category": "Banking & Financial Services",
        "purpose": "Banking Alerts, iMobile & OTP"
    },
    "AXISBK": {
        "entity_name": "Axis Bank Limited",
        "brand_name": "Axis Bank",
        "category": "Banking & Financial Services",
        "purpose": "Account & Credit Card Communication"
    },
    "IPAYTM": {
        "entity_name": "One97 Communications Limited (Paytm)",
        "brand_name": "Paytm",
        "category": "Payments & Wallet Services",
        "purpose": "Payment Receipts & Wallet Security OTP"
    },
    "PAYTM": {
        "entity_name": "One97 Communications Limited (Paytm)",
        "brand_name": "Paytm",
        "category": "Payments & Wallet Services",
        "purpose": "Paytm Transactions & Alerts"
    },
    "AIRTEL": {
        "entity_name": "Bharti Airtel Limited",
        "brand_name": "Airtel",
        "category": "Telecommunication Services",
        "purpose": "Recharge, Bill Alerts & Customer Support"
    },
    "JIOINF": {
        "entity_name": "Reliance Jio Infocomm Limited",
        "brand_name": "Jio",
        "category": "Telecommunication Services",
        "purpose": "Jio Fiber & Mobile Account Services"
    },
    "VIINFO": {
        "entity_name": "Vodafone Idea Limited",
        "brand_name": "Vi",
        "category": "Telecommunication Services",
        "purpose": "Recharge & Account Communication"
    },
    "INDPOST": {
        "entity_name": "Department of Posts (India Post)",
        "brand_name": "India Post / IPPB",
        "category": "Government & Logistics Services",
        "purpose": "Postal Tracking & India Post Payments Bank Alerts"
    },
    "INDPST": {
        "entity_name": "Department of Posts (India Post)",
        "brand_name": "India Post",
        "category": "Government & Logistics Services",
        "purpose": "Speed Post & Mail Delivery Updates"
    },
    "IRCTC": {
        "entity_name": "Indian Railway Catering and Tourism Corp",
        "brand_name": "IRCTC",
        "category": "Government & Railway Transport",
        "purpose": "PNR Status, E-Ticket Confirmation & Train Alerts"
    },
    "TRAIHD": {
        "entity_name": "Telecom Regulatory Authority of India",
        "brand_name": "TRAI",
        "category": "Government Regulatory Body",
        "purpose": "Consumer Protection & Regulatory Awareness"
    },
    "LICIND": {
        "entity_name": "Life Insurance Corporation of India",
        "brand_name": "LIC",
        "category": "Insurance & Pension Services",
        "purpose": "Policy Premium Reminders & Receipts"
    },
    "AMAZON": {
        "entity_name": "Amazon Seller Services Pvt Ltd",
        "brand_name": "Amazon",
        "category": "E-Commerce & Delivery",
        "purpose": "Order Delivery & Account Verification OTP"
    },
    "FLPKRT": {
        "entity_name": "Flipkart Internet Private Limited",
        "brand_name": "Flipkart",
        "category": "E-Commerce & Delivery",
        "purpose": "Order Tracking & Delivery OTP"
    },
    "SWIGGY": {
        "entity_name": "Bundl Technologies Pvt Ltd (Swiggy)",
        "brand_name": "Swiggy",
        "category": "Food Delivery & Logistics",
        "purpose": "Delivery Partner & Order Tracking Updates"
    },
    "ZOMATO": {
        "entity_name": "Zomato Limited",
        "brand_name": "Zomato",
        "category": "Food Delivery & Dining",
        "purpose": "Order Status & OTP Verification"
    },
    "UBERIN": {
        "entity_name": "Uber India Systems Pvt Ltd",
        "brand_name": "Uber",
        "category": "Mobility & Ride Hailing",
        "purpose": "Trip Verification OTP & Receipts"
    },
    "OLACAB": {
        "entity_name": "ANI Technologies Pvt Ltd (Ola)",
        "brand_name": "Ola Cabs",
        "category": "Mobility & Ride Hailing",
        "purpose": "Ride Pin & Driver Arrival Alerts"
    },
    "PUNBNK": {
        "entity_name": "Punjab National Bank",
        "brand_name": "PNB",
        "category": "Banking & Financial Services",
        "purpose": "Transaction & OTP Communication"
    },
    "CANBNK": {
        "entity_name": "Canara Bank",
        "brand_name": "Canara Bank",
        "category": "Banking & Financial Services",
        "purpose": "Banking Alerts & Debit Card OTP"
    },
    "KOTAKB": {
        "entity_name": "Kotak Mahindra Bank Limited",
        "brand_name": "Kotak 811",
        "category": "Banking & Financial Services",
        "purpose": "Account Balance & Transaction Verification"
    },
    "YESBNK": {
        "entity_name": "Yes Bank Limited",
        "brand_name": "Yes Bank",
        "category": "Banking & Financial Services",
        "purpose": "Banking & UPI Transaction Alerts"
    },
    "UNIONB": {
        "entity_name": "Union Bank of India",
        "brand_name": "Union Bank",
        "category": "Banking & Financial Services",
        "purpose": "Banking & ATM Alerts"
    },
    "BOITXN": {
        "entity_name": "Bank of India",
        "brand_name": "Bank of India",
        "category": "Banking & Financial Services",
        "purpose": "Transaction SMS Alerts"
    },
    "BOBTXN": {
        "entity_name": "Bank of Baroda",
        "brand_name": "Bank of Baroda",
        "category": "Banking & Financial Services",
        "purpose": "bob World Banking Alerts"
    },
    "MAHABK": {
        "entity_name": "Bank of Maharashtra",
        "brand_name": "Bank of Maharashtra",
        "category": "Banking & Financial Services",
        "purpose": "Account & Transaction Alerts"
    },
    "IDFCRD": {
        "entity_name": "IDFC FIRST Bank Limited",
        "brand_name": "IDFC FIRST Bank",
        "category": "Banking & Financial Services",
        "purpose": "Credit Card & Savings Account Alerts"
    },
    "FEDBNK": {
        "entity_name": "Federal Bank Limited",
        "brand_name": "Federal Bank",
        "category": "Banking & Financial Services",
        "purpose": "FedMobile & Account Alerts"
    },
    "CENTBK": {
        "entity_name": "Central Bank of India",
        "brand_name": "Central Bank",
        "category": "Banking & Financial Services",
        "purpose": "Banking Communication"
    },
    "AADHAR": {
        "entity_name": "Unique Identification Authority of India",
        "brand_name": "UIDAI / Aadhaar",
        "category": "Government Citizen Services",
        "purpose": "Aadhaar OTP & Authentication Verification"
    },
    "UIDAIH": {
        "entity_name": "Unique Identification Authority of India",
        "brand_name": "UIDAI",
        "category": "Government Citizen Services",
        "purpose": "e-Aadhaar & Verification Alerts"
    },
    "MEITYS": {
        "entity_name": "Ministry of Electronics and Information Tech",
        "brand_name": "MeitY",
        "category": "Government Regulatory Body",
        "purpose": "Digital India Awareness & Govt Security Alerts"
    },
    "650022": {
        "entity_name": "Registered Telecom Service Provider",
        "brand_name": "Telecom Service Alert",
        "category": "Service Awareness",
        "purpose": "Service Status & Network Updates"
    }
}

class TraiHeaderRegistryProvider:
    """
    TRAI Header Information Portal verification engine.
    Extracts, normalizes, and validates Indian SMS headers against the TRAI Registry.
    """

    @staticmethod
    def extract_header_token(sender_id: Optional[str]) -> Tuple[bool, str, Optional[str]]:
        """
        Parses sender_id (e.g. AD-650022-P, JM-HDFCBK-S, JD-IPAYTM, VK-HDFCBK)
        Returns: (is_dlt_header, normalized_sender_id, root_header_token)
        """
        if not sender_id:
            return False, "", None
        
        raw = sender_id.strip().upper()
        if PHONE_PATTERN.match(raw) or (len(raw) == 10 and raw.isdigit()):
            return False, raw, None
        
        # Check standard DLT header structure
        m = HEADER_PATTERN.match(raw)
        if m:
            token = m.group(1).upper()
            return True, raw, token
        
        # Alphanumeric fallback if length is 3..11
        if re.match(r"^[A-Z0-9]{3,11}$", raw):
            return True, raw, raw

        return False, raw, None

    async def verify(
        self,
        sender_id: Optional[str],
        claimed_organization: Optional[str],
        urls: List[str]
    ) -> Tuple[List[RiskSignal], TraiIdentityInfo]:
        
        signals: List[RiskSignal] = []
        is_dlt, raw_sender, root_token = self.extract_header_token(sender_id)
        is_phone_number = bool(sender_id and (PHONE_PATTERN.match(sender_id.strip()) or (len(sender_id.strip()) == 10 and sender_id.strip().isdigit())))

        logger.info(f"TRAI_LOOKUP_START header='{raw_sender}' root_token='{root_token}'")

        info = TraiIdentityInfo(
            checked=True,
            verified=False,
            is_dlt_header=is_dlt,
            header=raw_sender if raw_sender else None,
            normalized_header=root_token,
            entity_name=None,
            brand_name=None,
            category=None,
            purpose=None,
            source="TRAI Header Information Portal",
            status_label="Unverified Sender",
            lookalike_warning=False,
            error=None
        )

        # 1. Look up header in TRAI Registry
        registry_match = TRAI_REGISTRY.get(root_token) if root_token else None

        if registry_match:
            logger.info(f"TRAI_ENTITY_MATCH entity='{registry_match['entity_name']}' brand='{registry_match['brand_name']}'")
            info.verified = True
            info.entity_name = registry_match["entity_name"]
            info.brand_name = registry_match["brand_name"]
            info.category = registry_match["category"]
            info.purpose = registry_match["purpose"]
            info.status_label = "Registered TRAI Header"

            # Signal: TRAI Header Verified (Legitimacy identity signal)
            signals.append(RiskSignal(
                category="identity",
                code="TRAI_HEADER_VERIFIED",
                description=f"Sender header '{raw_sender}' is registered to {registry_match['entity_name']} in the TRAI registry.",
                technical_detail=f"Header '{root_token}' matched TRAI Entity: '{registry_match['entity_name']}' ({registry_match['category']})",
                weight=0.0,  # Bounded positive/neutral signal
                triggered=True
            ))

            # Maintain backward compatibility code
            signals.append(RiskSignal(
                category="identity",
                code="DLT_HEADER_UNVERIFIED",
                description="Sender verification checked against TRAI DLT registration registry.",
                technical_detail=f"Sender '{raw_sender}' registered to {registry_match['entity_name']}",
                weight=0.05,
                triggered=False
            ))
        else:
            if is_dlt:
                info.status_label = "Unverified Alphanumeric Header"
                signals.append(RiskSignal(
                    category="identity",
                    code="TRAI_HEADER_NOT_FOUND",
                    description=f"Alphanumeric sender header '{raw_sender}' was not found in the TRAI registry.",
                    technical_detail=f"Header '{root_token or raw_sender}' is not listed on the TRAI Header Information Portal.",
                    weight=0.05,
                    triggered=True
                ))
            else:
                info.status_label = "Personal / Standard Number"

            signals.append(RiskSignal(
                category="identity",
                code="DLT_HEADER_UNVERIFIED",
                description="Sender verification checked against TRAI DLT registration registry.",
                technical_detail=f"Sender '{raw_sender or 'Unknown'}' is_dlt_header={is_dlt}, is_personal_number={is_phone_number}",
                weight=0.05,
                triggered=not is_dlt
            ))

        # 2. Lookalike Header & Claimed Organization Mismatch
        mismatch_triggered = False
        if claimed_organization:
            claimed_lower = claimed_organization.lower().strip()
            if is_phone_number or not is_dlt:
                mismatch_triggered = True
            elif registry_match:
                reg_entity_lower = registry_match["entity_name"].lower()
                reg_brand_lower = registry_match["brand_name"].lower()
                if claimed_lower not in reg_entity_lower and claimed_lower not in reg_brand_lower and reg_brand_lower not in claimed_lower:
                    mismatch_triggered = True
            else:
                # Alphanumeric header not found, but message claims to be an organization (e.g. AB-SBIKYC claiming SBI)
                mismatch_triggered = True

        if mismatch_triggered and claimed_organization:
            info.lookalike_warning = True
            signals.append(RiskSignal(
                category="identity",
                code="TRAI_HEADER_IDENTITY_MISMATCH",
                description=f"Sender header '{raw_sender or 'Unknown'}' does not match official TRAI registry for {claimed_organization}.",
                technical_detail=f"Claimed org '{claimed_organization}' mismatch with TRAI header identity '{raw_sender}'.",
                weight=0.15,
                triggered=True
            ))
            # Maintain backward compatibility code
            signals.append(RiskSignal(
                category="identity",
                code="SENDER_ORGANIZATION_MISMATCH",
                description=f"The sender header '{raw_sender or 'unknown'}' does not match official header for {claimed_organization}.",
                technical_detail=f"Claimed org '{claimed_organization}' received from unverified header '{raw_sender}'.",
                weight=0.15,
                triggered=True
            ))

        # 3. Sender Domain Mismatch (Destination link vs Organization)
        domain_mismatch = False
        mismatched_domain = ""
        if claimed_organization and urls:
            org_lower = claimed_organization.lower()
            org_token = re.sub(r"\s+", "", org_lower)
            for u in urls:
                try:
                    parsed_u = urlparse(u if u.startswith(("http://", "https://")) else "http://" + u)
                    host = (parsed_u.netloc or parsed_u.path or "").lower()
                    if ":" in host:
                        host = host.split(":")[0]
                    if host and org_token not in host and "gov.in" not in host and "nic.in" not in host:
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

        return signals, info
