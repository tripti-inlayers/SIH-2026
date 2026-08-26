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

    # Triggered signals summing to 0.35 -> score 35 -> LOW
    sig_low = [
        RiskSignal(category="message", code="URGENCY", description="Urgent", technical_detail="", weight=0.35, triggered=True)
    ]
    score, level, conf, reasons, action, block, report = engine.fuse(sig_low, has_url=True)
    assert score == 35
    assert level == RiskLevel.LOW

    # Triggered signals summing to 0.40 -> score 40 -> SUSPICIOUS
    sig_susp = [
        RiskSignal(category="message", code="URGENCY", description="Urgent", technical_detail="", weight=0.40, triggered=True)
    ]
    score, level, conf, reasons, action, block, report = engine.fuse(sig_susp, has_url=True)
    assert score == 40
    assert level == RiskLevel.SUSPICIOUS

    # Triggered signals summing to 0.75 -> score 75 -> HIGH
    sig_high = [
        RiskSignal(category="message", code="CREDENTIAL", description="Cred", technical_detail="", weight=0.40, triggered=True),
        RiskSignal(category="url", code="LOOKALIKE", description="Lookalike", technical_detail="", weight=0.35, triggered=True)
    ]
    score, level, conf, reasons, action, block, report = engine.fuse(sig_high, has_url=True)
    assert score == 75
    assert level == RiskLevel.HIGH
    assert block is True
    assert report is True

def test_degraded_confidence_reduction():
    engine = RiskFusionEngine()
    sig = [RiskSignal(category="message", code="CODE", description="Desc", technical_detail="", weight=0.1, triggered=True)]
    _, _, conf_normal, _, _, _, _ = engine.fuse(sig, degraded=False)
    _, _, conf_degraded, _, _, _, _ = engine.fuse(sig, degraded=True)
    assert conf_degraded < conf_normal
