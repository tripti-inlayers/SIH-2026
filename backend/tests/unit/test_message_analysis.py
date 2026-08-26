import pytest
from app.services.message_analysis import MessageAnalysisService

def test_urgency_language_trigger():
    service = MessageAnalysisService()
    signals = service.analyze("Act now! Your account will be suspended within 24 hours.")
    urgency_signal = next(s for s in signals if s.code == "URGENCY_LANGUAGE")
    assert urgency_signal.triggered is True

def test_credential_request_trigger():
    service = MessageAnalysisService()
    signals = service.analyze("Please share your OTP and enter your secret PIN to verify.")
    cred_signal = next(s for s in signals if s.code == "CREDENTIAL_REQUEST")
    assert cred_signal.triggered is True

def test_hindi_hinglish_patterns():
    service = MessageAnalysisService()
    signals = service.analyze("Turant OTP bhejein nahi toh account band ho jayega.")
    urgency_signal = next(s for s in signals if s.code == "URGENCY_LANGUAGE")
    cred_signal = next(s for s in signals if s.code == "CREDENTIAL_REQUEST")
    assert urgency_signal.triggered is True
    assert cred_signal.triggered is True

def test_clean_message_no_trigger():
    service = MessageAnalysisService()
    signals = service.analyze("Hi, see you for dinner tomorrow at 7 PM.")
    triggered_codes = [s.code for s in signals if s.triggered]
    assert "CREDENTIAL_REQUEST" not in triggered_codes
    assert "URGENCY_LANGUAGE" not in triggered_codes
    assert "FINANCIAL_REQUEST" not in triggered_codes
