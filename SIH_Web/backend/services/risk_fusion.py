from typing import List, Tuple
from models.schemas import Signal

def fuse_signals(signals: List[Signal]) -> Tuple[float, str, str, str, bool]:
    # Base weights summing to 100
    BASE_WEIGHTS = {
        "ml_model": 35.0,
        "keyword_analyzer": 20.0,
        "url_lexical": 20.0,
        "threat_intel": 15.0,
        "identity_verifier": 10.0
    }
    
    active_weights = {}
    partial_analysis = False
    
    # Identify failed or timed-out signals
    for sig in signals:
        is_failed = ("timed out" in sig.description.lower() or 
                     "failed" in sig.description.lower() or 
                     "error" in sig.description.lower() or
                     "unavailable" in sig.description.lower())
        if is_failed:
            partial_analysis = True
        else:
            active_weights[sig.source] = BASE_WEIGHTS.get(sig.source, 0.0)
            
    total_active_weight = sum(active_weights.values())
    if total_active_weight == 0:
        return 0.0, "LOW", "ALLOW", "All analyzers failed or timed out.", True
        
    # Get active base weights
    ml_w = active_weights.get("ml_model", 0.0)
    kw_w = active_weights.get("keyword_analyzer", 0.0)
    lex_w = active_weights.get("url_lexical", 0.0)
    ti_w = active_weights.get("threat_intel", 0.0)
    id_w = active_weights.get("identity_verifier", 0.0)
    
    # Get confidences
    confidences = {sig.source: sig.confidence for sig in signals if sig.source in active_weights}
    ml_conf = confidences.get("ml_model", 0.0)
    kw_conf = confidences.get("keyword_analyzer", 0.0)
    lex_conf = confidences.get("url_lexical", 0.0)
    ti_conf = confidences.get("threat_intel", 0.0)
    id_conf = confidences.get("identity_verifier", 0.0)
    
    # Calculate raw weighted contributions
    ml_contrib = ml_conf * ml_w
    kw_contrib = kw_conf * kw_w
    lex_contrib = lex_conf * lex_w
    ti_contrib = ti_conf * ti_w
    id_contrib = id_conf * id_w
    
    # 1. Category Capping
    # Combine text/identity-based signals (ML, Keywords, and Identity Verifier)
    text_signals = [ml_contrib, kw_contrib, id_contrib]
    text_score = max(text_signals)
    # Add 20% of the non-max signals
    text_score += 0.2 * (sum(text_signals) - text_score)
        
    # Combine URL-based signals (Lexical and Threat Intel)
    url_score = max(lex_contrib, ti_contrib)
    if lex_contrib > 0.0 and ti_contrib > 0.0:
        url_score += 0.3 * min(lex_contrib, ti_contrib)
        
    # 2. Sub-additive combination
    combined_raw = text_score + url_score - ((text_score * url_score) / 100.0)
    
    # 3. Base Weight Denominator Normalization:
    # Denominator is always the Total Base Weight of all analyzers (100.0)
    # If an analyzer finds nothing, its confidence is 0 but base weight remains in the divisor.
    # If an analyzer failed/timed out, active weights are normalized across available analyzers.
    TOTAL_BASE_WEIGHT = 100.0
    if total_active_weight == TOTAL_BASE_WEIGHT:
        redistributed_score = combined_raw
    else:
        # Graceful normalization only when an analyzer truly fails / times out
        redistributed_score = (combined_raw / total_active_weight) * TOTAL_BASE_WEIGHT
        
    risk_score = round(max(0.0, min(100.0, redistributed_score)), 1)
    
    # 4. Critical Threat Overrides (Directly promote clear attacks to BLOCK)
    is_ssrf = any("ssrf" in sig.description.lower() for sig in signals)
    is_blacklist = any("newborn" in sig.description.lower() or "malicious" in sig.description.lower() for sig in signals)
    is_high_spoof = any("identity threat" in sig.description.lower() for sig in signals)
    
    if is_ssrf:
        risk_score = 98.0
        decision = "BLOCK"
        risk_level = "HIGH"
    elif is_blacklist or is_high_spoof:
        risk_score = 88.0
        decision = "BLOCK"
        risk_level = "HIGH"
    else:
        # Standard Decision Policy
        # 0-39: ALLOW
        # 40-79: WARN
        # 80-100: BLOCK
        if risk_score >= 80.0:
            decision = "BLOCK"
            risk_level = "HIGH"
        elif risk_score >= 40.0:
            decision = "WARN"
            risk_level = "MEDIUM"
        else:
            decision = "ALLOW"
            risk_level = "LOW"
        
    # 5. Generated explanation
    reasons = []
    # Identify primary and secondary drivers based on active contributions
    sorted_sigs = sorted(
        [s for s in signals if s.source in active_weights and s.confidence > 0.0],
        key=lambda x: x.confidence * BASE_WEIGHTS.get(x.source, 0.0),
        reverse=True
    )
    
    if sorted_sigs:
        reasons.append(f"Primary: {sorted_sigs[0].description}")
        if len(sorted_sigs) > 1:
            reasons.append(f"Secondary: {sorted_sigs[1].description}")
    else:
        reasons.append("No suspicious signals detected.")
        
    if partial_analysis:
        reasons.append("[Partial Analysis: Active weights redistributed.]")
        
    explanation = " ".join(reasons)
    
    return risk_score, risk_level, decision, explanation, partial_analysis
