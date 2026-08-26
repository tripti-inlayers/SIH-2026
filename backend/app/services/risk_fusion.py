from typing import List, Tuple
from app.schemas.common import RiskSignal, RiskLevel
from app.config import settings

class RiskFusionEngine:
    def fuse(
        self,
        signals: List[RiskSignal],
        has_url: bool = False,
        degraded: bool = False
    ) -> Tuple[int, RiskLevel, float, List[str], str, bool, bool]:
        """
        Fuses all computed signals into a final risk evaluation tuple:
        (risk_score, risk_level, confidence, reasons, recommended_action, should_block, should_report)
        """
        # 1. Accumulate raw score from triggered signals
        raw_score = sum(s.weight for s in signals if s.triggered)

        # 2. Normalize to 0 - 100 integer range
        risk_score = min(100, max(0, int(round(raw_score * 100))))

        # 3. Map to RiskLevel using configured thresholds
        if risk_score <= settings.RISK_THRESHOLD_LOW_MAX:
            risk_level = RiskLevel.LOW
        elif risk_score <= settings.RISK_THRESHOLD_SUSPICIOUS_MAX:
            risk_level = RiskLevel.SUSPICIOUS
        else:
            risk_level = RiskLevel.HIGH

        # 4. Confidence calculation
        # Base 0.60 + 0.10 for each unique triggered category
        triggered_categories = {s.category for s in signals if s.triggered}
        confidence = 0.60 + (0.10 * len(triggered_categories))
        confidence = min(0.95, confidence)
        if degraded:
            confidence = max(0.30, confidence - 0.20)
        confidence = round(confidence, 2)

        # 5. Extract top 4 plain-language reasons sorted by weight descending
        triggered_signals = [s for s in signals if s.triggered]
        triggered_signals.sort(key=lambda s: s.weight, reverse=True)
        reasons = [s.description for s in triggered_signals[:4]]

        if not reasons:
            if risk_level == RiskLevel.LOW:
                reasons = ["No major threat indicators detected."]
            else:
                reasons = ["Suspicious pattern detected in message structure."]

        # 6. Recommended Action
        if risk_level == RiskLevel.LOW:
            recommended_action = "No action needed, but stay cautious with unfamiliar links."
        elif risk_level == RiskLevel.SUSPICIOUS:
            recommended_action = "Verify the sender independently before acting."
        else:
            recommended_action = "Do not open this link or share any information."

        # 7. Action Flags
        should_block = (risk_level == RiskLevel.HIGH) and has_url
        should_report = (risk_level == RiskLevel.HIGH)

        return (risk_score, risk_level, confidence, reasons, recommended_action, should_block, should_report)
