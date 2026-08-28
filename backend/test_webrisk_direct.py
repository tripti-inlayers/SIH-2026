import asyncio
import sys
from app.services.threat_intel.google_webrisk import GoogleWebRiskProvider
from app.config import settings

async def main():
    print("=== GOOGLE WEB RISK DIRECT PROVIDER TEST ===")
    has_key = bool(settings.GOOGLE_WEBRISK_API_KEY)
    print(f"API Key Configured: {'YES' if has_key else 'NO'}")
    
    provider = GoogleWebRiskProvider()
    
    test_urls = [
        ("Malware Test URL", "http://testsafebrowsing.appspot.com/s/malware.html"),
        ("Phishing Test URL", "http://testsafebrowsing.appspot.com/s/phishing.html"),
        ("Clean URL", "https://www.google.com"),
    ]
    
    for label, url in test_urls:
        print(f"\n--- Testing {label} ---")
        print(f"URL: {url}")
        res = await provider.check_url(url)
        print(f"Available: {res['available']}")
        print(f"Matched: {res['matched']}")
        print(f"Threat Types: {res['threat_types']}")
        if res.get('error'):
            print(f"Error: {res['error']}")
        
        protocol_res = await provider.lookup(url)
        print(f"Verdict: {protocol_res.verdict}")
        print(f"Detail: {protocol_res.detail}")

if __name__ == "__main__":
    asyncio.run(main())
