import logging
import hashlib
from app.config import settings

def setup_logging():
    level = getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
    )

logger = logging.getLogger("sancharsaathi")

def redact_text(text: str) -> str:
    """Returns a hash and length representation of text for secure privacy logging."""
    if not text:
        return "<empty>"
    sha = hashlib.sha256(text.encode("utf-8")).hexdigest()[:8]
    return f"<text len={len(text)} hash={sha}>"
