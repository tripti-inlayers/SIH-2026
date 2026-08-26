from urllib.parse import urlparse

def sanitize_url(raw_url: str) -> str:
    """
    Sanitizes raw URL string:
    - Strips whitespace
    - Validates scheme is http or https
    - Rejects invalid/malformed URLs or non-HTTP schemes
    No server-side HTTP request is ever made to user-submitted URLs (SSRF prevention).
    """
    if not raw_url:
        raise ValueError("URL cannot be empty")
    
    cleaned = raw_url.strip()
    if len(cleaned) > 2048:
        cleaned = cleaned[:2048]
        
    parsed = urlparse(cleaned)
    if parsed.scheme.lower() not in ("http", "https"):
        raise ValueError(f"Unsupported URL scheme: {parsed.scheme}")
    if not parsed.netloc:
        raise ValueError("URL missing domain host")
        
    return cleaned
