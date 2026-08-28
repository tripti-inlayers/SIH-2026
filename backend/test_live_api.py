import httpx
import json

def test_live():
    base_url = "http://127.0.0.1:8000/api/v1"
    
    # 1. Health
    print("--- 1. Testing GET /health ---")
    r_health = httpx.get(f"{base_url}/health")
    print(f"Status: {r_health.status_code}")
    print("Response JSON:", json.dumps(r_health.json(), indent=2))
    
    # 2. Analyze URL Only
    print("\n--- 2. Testing POST /analyze/url ---")
    r_url = httpx.post(f"{base_url}/analyze/url", json={"url": "http://testsafebrowsing.appspot.com/s/malware.html"})
    print(f"Status: {r_url.status_code}")
    print("Response JSON:", json.dumps(r_url.json(), indent=2))

    # 3. Analyze Full Message with URL
    print("\n--- 3. Testing POST /analyze (Message with URL) ---")
    payload = {
        "message_id": "MSG-LIVE-001",
        "text": "URGENT: Your account has been suspended. Please verify at http://testsafebrowsing.appspot.com/s/malware.html",
        "urls": ["http://testsafebrowsing.appspot.com/s/malware.html"],
        "sender_id": "VK-HDFCBK",
        "claimed_organization": "HDFC Bank",
        "timestamp_epoch_millis": 1700000000000,
        "source": "REAL_SMS"
    }
    r_msg = httpx.post(f"{base_url}/analyze", json=payload)
    print(f"Status: {r_msg.status_code}")
    res_data = r_msg.json()
    print(f"Risk Score: {res_data.get('risk_score')}")
    print(f"Risk Level: {res_data.get('risk_level')}")
    print(f"Reasons: {res_data.get('reasons')}")
    print(f"Degraded: {res_data.get('degraded')} (Reason: {res_data.get('degraded_reason')})")

    # 4. Analyze Message Without URL
    print("\n--- 4. Testing POST /analyze (No-URL Scam) ---")
    payload_no_url = {
        "message_id": "MSG-LIVE-002",
        "text": "Dear customer, your electricity power will be disconnected tonight at 9:30 PM due to unpaid bill. Immediately send OTP to 9876543210.",
        "urls": [],
        "sender_id": "VM-EBILL",
        "claimed_organization": "Electricity Board",
        "timestamp_epoch_millis": 1700000000000,
        "source": "SMS"
    }
    r_no_url = httpx.post(f"{base_url}/analyze", json=payload_no_url)
    print(f"Status: {r_no_url.status_code}")
    res_no_url = r_no_url.json()
    print(f"Risk Score: {res_no_url.get('risk_score')}")
    print(f"Risk Level: {res_no_url.get('risk_level')}")
    print(f"Reasons: {res_no_url.get('reasons')}")

if __name__ == "__main__":
    test_live()
