from urllib.parse import urlparse
import re
from typing import List, Optional
from app.schemas.common import RiskSignal
from app.core.security import sanitize_url

SHORTENER_DOMAINS = {"bit.ly", "tinyurl.com", "t.co", "is.gd", "buff.ly", "ow.ly", "goo.gl"}
SUSPICIOUS_TLDS = {".tk", ".top", ".xyz", ".gq", ".ml", ".cf", ".ga", ".work", ".click"}
TARGET_BRANDS = ["sbi", "bankofindia", "statebankofindia", "hdfcbank", "icicibank", "axisbank", "indiapost", "irctc", "airtel", "jio"]

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
            weight=0.15,
            triggered=is_ip
        ))

        # 2. Non-HTTPS
        is_http = parsed.scheme.lower() == "http"
        signals.append(RiskSignal(
            category="url",
            code="NON_HTTPS",
            description="The link uses insecure HTTP instead of encrypted HTTPS.",
            technical_detail=f"Scheme used: {parsed.scheme}",
            weight=0.08,
            triggered=is_http
        ))

        # 3. Excessive Subdomains
        subdomain_parts = host.split(".")
        excessive_subdomains = len(subdomain_parts) > 4
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
            description="The link uses a top-level domain frequently associated with spam or scams.",
            technical_detail=f"TLD: {tld}",
            weight=0.07,
            triggered=is_suspicious_tld
        ))

        # 6. Suspicious Characters
        has_at = "@" in raw_url
        has_punycode = "xn--" in host
        excessive_hyphens = host.count("-") > 2
        suspicious_chars = has_at or has_punycode or excessive_hyphens
        signals.append(RiskSignal(
            category="url",
            code="SUSPICIOUS_CHARACTERS",
            description="The link contains obfuscated or suspicious character structures.",
            technical_detail=f"at_symbol={has_at}, punycode={has_punycode}, hyphens={host.count('-')}",
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

        # 8. Domain Lookalike (Levenshtein check on sub-tokens)
        host_tokens = re.split(r"[\.\-_]", host)
        is_lookalike = False
        matched_brand = ""
        for token in host_tokens:
            if not token or len(token) < 3:
                continue
            normalized_token = token.replace("0", "o").replace("1", "i").replace("3", "e").replace("5", "s")
            for brand in TARGET_BRANDS:
                if token == brand:
                    continue  # exact match on official brand token
                dist = levenshtein_distance(normalized_token, brand)
                if dist <= 2 and abs(len(normalized_token) - len(brand)) <= 2:
                    is_lookalike = True
                    matched_brand = brand
                    break
            if is_lookalike:
                break

        signals.append(RiskSignal(
            category="url",
            code="DOMAIN_LOOKALIKE",
            description=f"The domain closely mimics a legitimate brand name ({matched_brand or 'known brand'}).",
            technical_detail=f"Host '{host}' token matched brand '{matched_brand}'",
            weight=0.15,
            triggered=is_lookalike
        ))

        return signals
