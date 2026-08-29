from urllib.parse import urlparse
import re
from typing import List, Optional
from app.schemas.common import RiskSignal
from app.core.security import sanitize_url

SHORTENER_DOMAINS = {"bit.ly", "tinyurl.com", "t.co", "is.gd", "buff.ly", "ow.ly", "goo.gl"}
SUSPICIOUS_TLDS = {".tk", ".top", ".xyz", ".gq", ".ml", ".cf", ".ga", ".work", ".click", ".shop", ".site", ".online", ".info"}

TARGET_BRANDS = [
    "amazon", "amazn", "google", "googl", "g-security", "paytm", "sbi", "statebank", "bankofindia",
    "statebankofindia", "hdfc", "hdfcbank", "icici", "icicibank", "axis", "axisbank", "airtel", "jio", 
    "indiapost", "irctc", "flipkart", "phonepe", "gpay", "paypal", "microsoft", "apple", "netflix", 
    "bank", "gov", "uidai", "aadhaar", "incometax"
]

SECURITY_INTENT_KEYWORDS = {
    "login", "verify", "auth", "security", "account", "update", "signin", "billing", 
    "credential", "support", "service", "confirm", "kyc", "alert", "portal"
}

def levenshtein_distance(s1: str, s2: str) -> int:
    if len(s1) < len(s2):
        return levenshtein_distance(s2, s1)
    if len(s2) == 0:
        return len(s1)
    previous_row = range(len(s2) + 1)
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row
    return previous_row[-1]

class UrlAnalysisService:
    def analyze(self, raw_url: str) -> List[RiskSignal]:
        signals: List[RiskSignal] = []
        try:
            url = sanitize_url(raw_url)
        except Exception as e:
            signals.append(RiskSignal(
                category="url",
                code="INVALID_URL",
                description="The URL format is invalid or malformed.",
                technical_detail=str(e),
                weight=0.20,
                triggered=True
            ))
            return signals

        parsed = urlparse(url)
        host = (parsed.netloc or "").lower()
        if ":" in host:
            host = host.split(":")[0]

        # 1. IP Address Host
        is_ip = bool(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", host))
        signals.append(RiskSignal(
            category="url",
            code="IP_ADDRESS_HOST",
            description="The link points directly to an IP address instead of a domain name.",
            technical_detail=f"Host resolved as raw IP: {host}",
            weight=0.25,
            triggered=is_ip
        ))

        # 2. Non-HTTPS (Insecure HTTP is a MINOR transport signal)
        is_http = parsed.scheme.lower() == "http"
        signals.append(RiskSignal(
            category="url",
            code="NON_HTTPS",
            description="The link uses insecure HTTP instead of encrypted HTTPS.",
            technical_detail=f"Scheme used: {parsed.scheme}",
            weight=0.05,  # Reduced so HTTP alone never forces high scores
            triggered=is_http
        ))

        # 3. Excessive Subdomains
        subdomain_parts = host.split(".")
        excessive_subdomains = len(subdomain_parts) > 3
        signals.append(RiskSignal(
            category="url",
            code="EXCESSIVE_SUBDOMAINS",
            description="The domain has an unusually high number of subdomains.",
            technical_detail=f"Subdomain label count: {len(subdomain_parts)}",
            weight=0.08,
            triggered=excessive_subdomains
        ))

        # 4. URL Shortener
        is_shortener = host in SHORTENER_DOMAINS
        signals.append(RiskSignal(
            category="url",
            code="URL_SHORTENER",
            description="The link uses a URL shortening service that hides its true destination.",
            technical_detail=f"Shortener host: {host}",
            weight=0.10,
            triggered=is_shortener
        ))

        # 5. Suspicious TLD
        tld = "." + host.split(".")[-1] if "." in host else ""
        is_suspicious_tld = tld in SUSPICIOUS_TLDS
        signals.append(RiskSignal(
            category="url",
            code="SUSPICIOUS_TLD",
            description="The link uses a top-level domain frequently associated with scam volume.",
            technical_detail=f"TLD: {tld}",
            weight=0.10,
            triggered=is_suspicious_tld
        ))

        # 6. Suspicious Characters / Hyphen Abuse
        has_at = "@" in raw_url
        has_punycode = "xn--" in host
        hyphen_count = host.count("-")
        excessive_hyphens = hyphen_count >= 2
        suspicious_chars = has_at or has_punycode or excessive_hyphens
        signals.append(RiskSignal(
            category="url",
            code="SUSPICIOUS_CHARACTERS",
            description="The link domain uses excessive hyphens or obfuscated characters.",
            technical_detail=f"at_symbol={has_at}, punycode={has_punycode}, hyphens={hyphen_count}",
            weight=0.08,
            triggered=suspicious_chars
        ))

        # 7. Unusual Length
        is_long = len(raw_url) > 100
        signals.append(RiskSignal(
            category="url",
            code="UNUSUAL_LENGTH",
            description="The link is unusually long and complex.",
            technical_detail=f"Length: {len(raw_url)} chars",
            weight=0.05,
            triggered=is_long
        ))

        # 8. Domain Tokens & Security Keyword Heuristics
        host_tokens = re.split(r"[\.\-_]", host)
        matched_security_keywords = [tok for tok in host_tokens if tok in SECURITY_INTENT_KEYWORDS]
        
        has_security_keyword = len(matched_security_keywords) > 0
        signals.append(RiskSignal(
            category="url",
            code="SECURITY_INTENT_KEYWORD",
            description=f"The domain name contains security/login intent keywords ({', '.join(matched_security_keywords)}).",
            technical_detail=f"Host '{host}' contains keywords: {matched_security_keywords}",
            weight=0.15,
            triggered=has_security_keyword
        ))

        # 9. Domain Lookalike & Brand Typosquatting
        is_lookalike = False
        matched_brand = ""
        
        # Check sub-tokens against brand registry
        for token in host_tokens:
            if not token or len(token) < 3:
                continue
            normalized_token = token.replace("0", "o").replace("1", "i").replace("3", "e").replace("5", "s")
            
            for brand in TARGET_BRANDS:
                if token == brand or normalized_token == brand:
                    if host.endswith(f".{brand}.com") or host == f"{brand}.com" or host.endswith(".gov.in"):
                        continue
                    is_lookalike = True
                    matched_brand = brand
                    break
                
                dist = levenshtein_distance(normalized_token, brand)
                if dist <= 2 and abs(len(normalized_token) - len(brand)) <= 2:
                    if host.endswith(f".{brand}.com") or host == f"{brand}.com":
                        continue
                    is_lookalike = True
                    matched_brand = brand
                    break
            if is_lookalike:
                break

        signals.append(RiskSignal(
            category="url",
            code="DOMAIN_LOOKALIKE",
            description=f"The domain closely mimics a known brand or institution ({matched_brand or 'popular brand'}).",
            technical_detail=f"Host '{host}' token matched brand target '{matched_brand}'",
            weight=0.20,
            triggered=is_lookalike
        ))

        # 10. Brand + Security Keyword Combination (High Risk Impersonation Pattern)
        is_brand_security_combo = is_lookalike and has_security_keyword
        signals.append(RiskSignal(
            category="url",
            code="BRAND_SECURITY_IMPERSONATION",
            description="The domain combines brand lookalikes with security/login verification keywords.",
            technical_detail=f"Brand '{matched_brand}' combined with security keywords {matched_security_keywords} in host '{host}'",
            weight=0.20,
            triggered=is_brand_security_combo
        ))

        return signals
