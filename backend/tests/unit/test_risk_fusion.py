import pytest
from app.services.risk_fusion import RiskFusionEngine
from app.schemas.common import RiskSignal, RiskLevel

def test_risk_fusion_level_mapping_boundaries():
    engine = RiskFusionEngine()
    
    # 0 weight -> score 0 -> LOW
    score, level, conf, reasons, action, block, report = engine.fuse([], has_url=True)
    assert score == 0
    assert level == RiskLevel.LOW
    assert block is False
    assert report is False

    # Triggered local message signal: weight 0.35. Contribution = 0.35 * 12.5 = 4.375 -> score 4 -> LOW
    sig_low = [
        RiskSignal(category="message", code="URGENCY", description="Urgent", technical_detail="", weight=0.35, triggered=True)
    ]
    score, level, conf, reasons, action, block, report = engine.fuse(sig_low, has_url=True)
    assert score == 11
    assert level == RiskLevel.LOW

    # Triggered PhishDestroy score 50 + ML spam probability 0.80 -> 30 + 20 = 50 -> SUSPICIOUS
    sig_susp = [
        RiskSignal(category="threat_intel", code="REPUTATION_MALICIOUS", description="Threat", technical_detail="", weight=0.50, triggered=True)
    ]
    score, level, conf, reasons, action, block, report = engine.fuse(
        sig_susp, 
        has_url=True,
        phishdestroy_score=50,
        ml_spam_probability=0.80
    )
    assert score == 50
    assert level == RiskLevel.SUSPICIOUS

    # Triggered PhishDestroy score 85 -> triggers Threat Floor of at least 80 -> HIGH
    sig_high = [
        RiskSignal(category="threat_intel", code="REPUTATION_MALICIOUS", description="Threat", technical_detail="", weight=0.85, triggered=True)
    ]
    score, level, conf, reasons, action, block, report = engine.fuse(
        sig_high, 
        has_url=True,
        phishdestroy_score=85,
        phishdestroy_threat=True
    )
    assert score >= 80
    assert level == RiskLevel.HIGH
    assert block is True
    assert report is True

def test_degraded_confidence_reduction():
    engine = RiskFusionEngine()
    sig = [RiskSignal(category="message", code="CODE", description="Desc", technical_detail="", weight=0.1, triggered=True)]
    _, _, conf_normal, _, _, _, _ = engine.fuse(sig, degraded=False)
    _, _, conf_degraded, _, _, _, _ = engine.fuse(sig, degraded=True)
    assert conf_degraded < conf_normal
