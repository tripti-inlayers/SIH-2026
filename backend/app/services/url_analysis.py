from urllib.parse import urlparse
import re
import math
from typing import List, Optional, Dict, Any
from pydantic import BaseModel
from app.schemas.common import RiskSignal
from app.core.security import sanitize_url

SHORTENER_DOMAINS = {
    "bit.ly", "tinyurl.com", "t.co", "is.gd", "buff.ly", 
    "ow.ly", "goo.gl", "rebrand.ly", "tiny.cc", "cutt.ly"
}
SUSPICIOUS_TLDS = {".tk", ".top", ".xyz", ".gq", ".ml", ".cf", ".ga", ".work", ".click", ".zip", ".mov"}
TARGET_BRANDS = ["sbi", "bankofindia", "statebankofindia", "hdfcbank", "icicibank", "axisbank", "indiapost", "irctc", "airtel", "jio"]
SUSPICIOUS_KEYWORDS = [
    "login", "verify", "kyc", "update", "account", "bank", "secure", 
    "confirm", "passcode", "credential", "unlock", "password", "payment", 
    "wallet", "refund", "claim", "signin", "sign-in", "authenticate", "pay-bill", "code"
]
SUSPICIOUS_EXTENSIONS = [".php", ".apk", ".exe", ".scr", ".bat", ".vbs"]

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

def calculate_shannon_entropy(text: str) -> float:
    """Calculates Shannon Entropy (bits per character) of a text string."""
    if not text:
        return 0.0
    length = len(text)
    counts: Dict[str, int] = {}
    for char in text.lower():
        counts[char] = counts.get(char, 0) + 1
    entropy = 0.0
    for count in counts.values():
        p = count / length
        entropy -= p * math.log2(p)
    return round(entropy, 3)

class UrlFeatures(BaseModel):
    """
    Structured feature vector for URL risk analysis.
    Designed for full compatibility with future ML classifiers (LightGBM/XGBoost).
    """
    raw_url: str
    original_url: Optional[str] = None
    scheme: str
    host: str
    is_ip: bool
    url_length: int
    hostname_length: int
    path_length: int
    query_length: int
    query_param_count: int
    subdomain_count: int
    digit_count: int
    letter_count: int
    digit_letter_ratio: float
    hyphen_count: int
    special_char_count: int
    has_punycode: bool
    has_suspicious_tld: bool
    suspicious_path_keywords: List[str]
    suspicious_query_keywords: List[str]
    hostname_entropy: float
    entropy_high: bool
    digit_letter_mix_high: bool
    is_shortened: bool
    brand_lookalike_matched: Optional[str] = None

def extract_url_features(url_string: str, original_url: Optional[str] = None) -> UrlFeatures:
    """Extracts raw numerical, boolean, and categorical features from a URL."""
    try:
        clean_url = sanitize_url(url_string)
    except Exception:
        clean_url = url_string.strip()

    parsed = urlparse(clean_url)
    scheme = parsed.scheme.lower() if parsed.scheme else "http"
    host = (parsed.netloc or "").lower().split(":")[0]

    is_ip = bool(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", host))

    # Host main label for entropy/digit analysis (ignoring TLD and www)
    subdomain_parts = host.split(".")
    if is_ip:
        main_label = host
        subdomain_count = 0
    else:
        subdomain_count = max(0, len(subdomain_parts) - 2)
        if len(subdomain_parts) > 1 and subdomain_parts[0] == "www":
            main_label = subdomain_parts[1] if len(subdomain_parts) > 2 else subdomain_parts[0]
        elif len(subdomain_parts) >= 2:
            main_label = subdomain_parts[-2]
        else:
            main_label = host

    digit_count = sum(c.isdigit() for c in main_label)
    letter_count = sum(c.isalpha() for c in main_label)
    total_alphanumeric = digit_count + letter_count
    digit_letter_ratio = round(digit_count / float(letter_count), 3) if letter_count > 0 else (float(digit_count) if digit_count > 0 else 0.0)

    # Entropy calculation on main domain label
    hostname_entropy = calculate_shannon_entropy(main_label)
    # High entropy: short random label (3-8 chars) with entropy >= 1.80, or longer label with entropy >= 3.20
    entropy_high = (len(main_label) >= 4 and len(main_label) <= 10 and hostname_entropy >= 1.80 and digit_count >= 1) or (hostname_entropy >= 3.20)

    # High digit-letter mix: main label contains at least 2 digits and 2 letters with ratio >= 0.30
    digit_letter_mix_high = (digit_count >= 2 and letter_count >= 2 and digit_letter_ratio >= 0.30)

    path = parsed.path or ""
    query = parsed.query or ""
    path_length = len(path)
    query_length = len(query)
    query_param_count = len(query.split("&")) if query else 0

    hyphen_count = host.count("-")
    special_char_count = sum(c in "@?&=_-%" for c in clean_url)
    has_punycode = "xn--" in host

    tld = "." + subdomain_parts[-1] if subdomain_parts else ""
    has_suspicious_tld = tld in SUSPICIOUS_TLDS

    path_query = (path + "?" + query).lower()
    path_kw_found = [kw for kw in SUSPICIOUS_KEYWORDS if kw in path.lower()]
    query_kw_found = [kw for kw in SUSPICIOUS_KEYWORDS if kw in query.lower()]

    is_shortened = host in SHORTENER_DOMAINS

    # Brand lookalike
    host_tokens = re.split(r"[\.\-_]", host)
    brand_matched = None
    for token in host_tokens:
        if not token or len(token) < 3:
            continue
        normalized_token = token.replace("0", "o").replace("1", "i").replace("3", "e").replace("5", "s")
        for brand in TARGET_BRANDS:
            if token == brand:
                continue
            dist = levenshtein_distance(normalized_token, brand)
            if dist <= 2 and abs(len(normalized_token) - len(brand)) <= 2:
                brand_matched = brand
                break
        if brand_matched:
            break

    return UrlFeatures(
        raw_url=clean_url,
        original_url=original_url,
        scheme=scheme,
        host=host,
        is_ip=is_ip,
        url_length=len(clean_url),
        hostname_length=len(host),
        path_length=path_length,
        query_length=query_length,
        query_param_count=query_param_count,
        subdomain_count=subdomain_count,
        digit_count=digit_count,
        letter_count=letter_count,
        digit_letter_ratio=digit_letter_ratio,
        hyphen_count=hyphen_count,
        special_char_count=special_char_count,
        has_punycode=has_punycode,
        has_suspicious_tld=has_suspicious_tld,
        suspicious_path_keywords=path_kw_found,
        suspicious_query_keywords=query_kw_found,
        hostname_entropy=hostname_entropy,
        entropy_high=entropy_high,
        digit_letter_mix_high=digit_letter_mix_high,
        is_shortened=is_shortened,
        brand_lookalike_matched=brand_matched
    )

class UrlAnalysisService:
    def analyze(self, raw_url: str, original_url: Optional[str] = None) -> List[RiskSignal]:
        signals: List[RiskSignal] = []
        try:
            features = extract_url_features(raw_url, original_url)
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

        # 1. IP Address Host
        signals.append(RiskSignal(
            category="url",
            code="IP_ADDRESS_HOST",
            description="The link points directly to an IP address instead of a domain name.",
            technical_detail=f"Host resolved as raw IP: {features.host}",
            weight=0.15,
            triggered=features.is_ip
        ))

        # 2. Insecure Protocol
        signals.append(RiskSignal(
            category="url",
            code="NON_HTTPS",
            description="The link uses insecure HTTP instead of encrypted HTTPS.",
            technical_detail=f"Scheme used: {features.scheme}",
            weight=0.08,
            triggered=(features.scheme == "http")
        ))

        # 3. Excessive Subdomains
        signals.append(RiskSignal(
            category="url",
            code="EXCESSIVE_SUBDOMAINS",
            description="The domain contains multiple subdomain levels.",
            technical_detail=f"Subdomain level count: {features.subdomain_count}",
            weight=0.08,
            triggered=(features.subdomain_count > 1 and not features.is_ip)
        ))

        # 4. URL Shortener Host
        signals.append(RiskSignal(
            category="url",
            code="URL_SHORTENER",
            description="The link uses a URL shortening service that hides its true destination.",
            technical_detail=f"Shortener host: {features.host}",
            weight=0.10,
            triggered=features.is_shortened
        ))

        # 5. Suspicious TLD
        signals.append(RiskSignal(
            category="url",
            code="SUSPICIOUS_TLD",
            description="The link uses a top-level domain frequently associated with spam or scams.",
            technical_detail=f"Host: {features.host}",
            weight=0.07,
            triggered=features.has_suspicious_tld
        ))

        # 6. Suspicious Characters (@, punycode, excessive hyphens)
        has_at = "@" in raw_url
        excessive_hyphens = features.hyphen_count > 2
        signals.append(RiskSignal(
            category="url",
            code="SUSPICIOUS_CHARACTERS",
            description="The link contains obfuscated or suspicious character structures.",
            technical_detail=f"at_symbol={has_at}, punycode={features.has_punycode}, hyphens={features.hyphen_count}",
            weight=0.08,
            triggered=(has_at or features.has_punycode or excessive_hyphens)
        ))

        # 7. Domain Randomness / Shannon Entropy
        signals.append(RiskSignal(
            category="url",
            code="DOMAIN_ENTROPY",
            description="Domain name exhibits unusually high character randomness or entropy.",
            technical_detail=f"Domain main label entropy: {features.hostname_entropy} bits/char",
            weight=0.10,
            triggered=features.entropy_high
        ))

        # 8. Digit/Letter Mixture
        signals.append(RiskSignal(
            category="url",
            code="DIGIT_LETTER_MIX",
            description="Domain contains an unusual mixture of numbers and letters.",
            technical_detail=f"Digits: {features.digit_count}, Letters: {features.letter_count}, Ratio: {features.digit_letter_ratio}",
            weight=0.08,
            triggered=features.digit_letter_mix_high
        ))

        # 9. Suspicious Path or Query Keywords
        has_path_query_kw = bool(features.suspicious_path_keywords or features.suspicious_query_keywords)
        matched_kws = list(set(features.suspicious_path_keywords + features.suspicious_query_keywords))
        signals.append(RiskSignal(
            category="url",
            code="SUSPICIOUS_PATH_QUERY",
            description="Sensitive action keyword detected in URL path or query parameters.",
            technical_detail=f"Matched keywords: {', '.join(matched_kws) if matched_kws else 'None'}",
            weight=0.10,
            triggered=has_path_query_kw
        ))

        # 10. Unusual Length
        signals.append(RiskSignal(
            category="url",
            code="UNUSUAL_LENGTH",
            description="The link is unusually long and complex.",
            technical_detail=f"Length: {features.url_length} chars",
            weight=0.05,
            triggered=(features.url_length > 100)
        ))

        # 11. Domain Lookalike
        signals.append(RiskSignal(
            category="url",
            code="DOMAIN_LOOKALIKE",
            description=f"The domain closely mimics a legitimate brand name ({features.brand_lookalike_matched or 'known brand'}).",
            technical_detail=f"Host '{features.host}' matched brand '{features.brand_lookalike_matched}'",
            weight=0.15,
            triggered=bool(features.brand_lookalike_matched)
        ))

        return signals
