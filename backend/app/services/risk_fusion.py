from typing import List, Tuple
import re
from app.schemas.common import RiskSignal, RiskLevel
from app.config import settings

class RiskFusionEngine:
    def fuse(
        self,
        signals: List[RiskSignal],
        has_url: bool = False,
        degraded: bool = False,
        phishdestroy_score: int = 0,
        phishdestroy_threat: bool = False,
        ml_spam_probability: float = None
    ) -> Tuple[int, RiskLevel, float, List[str], str, bool, bool]:
        """
        Fuses all computed signals into a final risk evaluation tuple:
        (risk_score, risk_level, confidence, reasons, recommended_action, should_block, should_report)
        """
        # 1. PhishDestroy / URL Threat Intelligence contribution (max 60 points)
        if phishdestroy_score > 0:
            pd_risk_score = phishdestroy_score
        else:
            pd_risk_score = 0
            for s in signals:
                if s.category == "threat_intel" and s.triggered:
                    pd_risk_score = max(pd_risk_score, int(round(s.weight * 100)))

        pd_contribution = min(60.0, pd_risk_score * 0.60)

        # 2. RoBERTa / Message Model contribution (max 25 points)
        if ml_spam_probability is not None:
            spam_prob = ml_spam_probability
        else:
            spam_prob = 0.0
            for s in signals:
                if s.category == "ml_model":
                    match = re.search(r"confidence:\s*([\d\.]+)", s.technical_detail)
                    if match:
                        conf = float(match.group(1))
                        if s.code == "AI_SPAM_DETECTED":
                            spam_prob = conf
                        else:
                            spam_prob = 1.0 - conf
                    else:
                        if s.code == "AI_SPAM_DETECTED":
                            spam_prob = 0.80
                        else:
                            spam_prob = 0.05

        model_contribution = min(25.0, spam_prob * 25.0)

        # 3. Local Message Rules contribution (max 10 points)
        local_weights_sum = sum(s.weight for s in signals if s.category == "message" and s.triggered)
        local_rule_contribution = min(10.0, local_weights_sum * 12.5)

        # 4. General URL Heuristics contribution (max 5 points)
        url_heuristics_sum = sum(s.weight for s in signals if s.category == "url" and s.triggered)
        url_heuristic_contribution = min(5.0, url_heuristics_sum * 5.2)

        # Fused raw score
        is_threat = phishdestroy_threat
        if not is_threat:
            for s in signals:
                if s.category == "threat_intel" and s.triggered and s.code in (
                    "WEBRISK_MALWARE", "WEBRISK_PHISHING", "WEBRISK_UNWANTED_SOFTWARE", "REPUTATION_MALICIOUS"
                ):
                    is_threat = True
                    break

        if not is_threat:
            # Scale other components to a 100-point scale
            raw_sum = model_contribution + local_rule_contribution + url_heuristic_contribution
            final_score = raw_sum * 2.5
        else:
            final_score = pd_contribution + model_contribution + local_rule_contribution + url_heuristic_contribution

        risk_score = min(100, max(0, int(round(final_score))))

        # Apply Confirmed Threat Floor:
        is_threat = phishdestroy_threat
        if not is_threat:
            for s in signals:
                if s.category == "threat_intel" and s.triggered and s.code in (
                    "WEBRISK_MALWARE", "WEBRISK_PHISHING", "WEBRISK_UNWANTED_SOFTWARE", "REPUTATION_MALICIOUS"
                ):
                    is_threat = True
                    break

        if is_threat and pd_risk_score >= 70:
            risk_score = max(risk_score, 80)

        # Map to RiskLevel
        if risk_score <= settings.RISK_THRESHOLD_LOW_MAX:
            risk_level = RiskLevel.LOW
        elif risk_score <= settings.RISK_THRESHOLD_SUSPICIOUS_MAX:
            risk_level = RiskLevel.SUSPICIOUS
        else:
            risk_level = RiskLevel.HIGH

        # Confidence calculation
        triggered_categories = {s.category for s in signals if s.triggered}
        confidence = 0.60 + (0.10 * len(triggered_categories))
        confidence = min(0.95, confidence)
        if degraded:
            confidence = max(0.30, confidence - 0.20)
        confidence = round(confidence, 2)

        # Extract reasons
        triggered_signals = [s for s in signals if s.triggered]
        triggered_signals.sort(key=lambda s: s.weight, reverse=True)
        reasons = [s.description for s in triggered_signals[:4]]

        if not reasons:
            if risk_level == RiskLevel.LOW:
                reasons = ["No major threat indicators detected."]
            else:
                reasons = ["Suspicious pattern detected in message structure."]

        # Recommended Action
        if risk_level == RiskLevel.LOW:
            recommended_action = "No action needed, but stay cautious with unfamiliar links."
        elif risk_level == RiskLevel.SUSPICIOUS:
            recommended_action = "Verify the sender independently before acting."
        else:
            recommended_action = "Do not open this link or share any information."

        should_block = (risk_level == RiskLevel.HIGH) and has_url
        should_report = (risk_level == RiskLevel.HIGH)

        return (risk_score, risk_level, confidence, reasons, recommended_action, should_block, should_report)
