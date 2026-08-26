import pytest
from app.services.url_analysis import UrlAnalysisService

def test_ip_address_host():
    service = UrlAnalysisService()
    signals = service.analyze("http://192.168.1.1/login")
    ip_signal = next(s for s in signals if s.code == "IP_ADDRESS_HOST")
    http_signal = next(s for s in signals if s.code == "NON_HTTPS")
    assert ip_signal.triggered is True
    assert http_signal.triggered is True

def test_suspicious_tld_and_lookalike():
    service = UrlAnalysisService()
    signals = service.analyze("http://secure-bank0findia-verify.xyz/login")
    tld_signal = next(s for s in signals if s.code == "SUSPICIOUS_TLD")
    lookalike_signal = next(s for s in signals if s.code == "DOMAIN_LOOKALIKE")
    assert tld_signal.triggered is True
    assert lookalike_signal.triggered is True

def test_clean_https_url():
    service = UrlAnalysisService()
    signals = service.analyze("https://www.indiapost.gov.in/track/12345")
    ip_signal = next(s for s in signals if s.code == "IP_ADDRESS_HOST")
    http_signal = next(s for s in signals if s.code == "NON_HTTPS")
    tld_signal = next(s for s in signals if s.code == "SUSPICIOUS_TLD")
    assert ip_signal.triggered is False
    assert http_signal.triggered is False
    assert tld_signal.triggered is False
