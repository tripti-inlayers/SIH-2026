import pytest
from app.services.url_analysis import (
    UrlAnalysisService, extract_url_features, calculate_shannon_entropy
)

def test_calculate_shannon_entropy():
    # Constant string has 0 entropy
    assert calculate_shannon_entropy("aaaaa") == 0.0
    # High entropy string
    ent = calculate_shannon_entropy("r9e9i")
    assert ent > 1.5

def test_extract_url_features_clean_url():
    feat = extract_url_features("https://www.google.com")
    assert feat.scheme == "https"
    assert feat.host == "www.google.com"
    assert feat.is_ip is False
    assert feat.has_punycode is False
    assert feat.entropy_high is False

def test_extract_url_features_random_digit_domain():
    feat = extract_url_features("https://r9e9i.com/?code=C1W5TNS")
    assert feat.digit_count == 2
    assert feat.letter_count == 3
    assert feat.digit_letter_ratio > 0.5
    assert feat.digit_letter_mix_high is True
    assert "code" in feat.suspicious_query_keywords

def test_url_analysis_service_ip_and_http():
    service = UrlAnalysisService()
    signals = service.analyze("http://192.168.1.1/login")
    codes = {s.code for s in signals if s.triggered}
    assert "IP_ADDRESS_HOST" in codes
    assert "NON_HTTPS" in codes
    assert "SUSPICIOUS_PATH_QUERY" in codes

def test_url_analysis_service_punycode_and_subdomains():
    service = UrlAnalysisService()
    signals = service.analyze("https://sub1.sub2.sub3.xn--example.com")
    codes = {s.code for s in signals if s.triggered}
    assert "EXCESSIVE_SUBDOMAINS" in codes
    assert "SUSPICIOUS_CHARACTERS" in codes

def test_url_analysis_service_typosquatting():
    service = UrlAnalysisService()
    signals = service.analyze("http://sbi0-verify.com/login")
    codes = {s.code for s in signals if s.triggered}
    assert "DOMAIN_LOOKALIKE" in codes
