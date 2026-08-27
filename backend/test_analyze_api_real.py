import httpx
import json

base_url = "http://localhost:8000/api/v1/analyze"

payload = {
    "message_id": "MSG-REAL-001",
    "text": "URGENT: Your account is suspended. Click http://bad.com",
    "urls": ["http://bad.com"],
    "sender_id": "UNKNOWN",
    "timestamp_epoch_millis": 1700000000000,
    "source": "SMS"
}

print("--- Calling /api/v1/analyze (ML Service is running) ---")
try:
    response = httpx.post(base_url, json=payload, timeout=10.0)
    print(f"Status: {response.status_code}")
    data = response.json()
    print("Risk Level:", data.get("risk_level"))
    print("Risk Score:", data.get("risk_score"))
    
    ml_signal = next((s for s in data.get("signals", []) if s["code"] == "AI_SPAM_DETECTED"), None)
    if ml_signal:
        print("ML Signal found! It was triggered:", ml_signal["triggered"])
        print("Description:", ml_signal["description"])
    else:
        print("ML Signal NOT found in response!")
except Exception as e:
    print(f"Error: {e}")
