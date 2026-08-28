import httpx
from urllib.parse import urlparse
from pydantic import BaseModel
from typing import List
from app.config import settings
from app.core.logging import logger

SHORTENER_DOMAINS = {
    "bit.ly", "tinyurl.com", "t.co", "is.gd", "buff.ly", 
    "ow.ly", "goo.gl", "rebrand.ly", "tiny.cc", "cutt.ly"
}

class ExpandedUrlResult(BaseModel):
    original_url: str
    resolved_url: str
    redirect_chain: List[str]
    is_shortened: bool
    hops: int

class UrlExpanderService:
    async def expand(self, url: str) -> ExpandedUrlResult:
        if not url or not url.strip():
            return ExpandedUrlResult(
                original_url=url,
                resolved_url=url,
                redirect_chain=[url],
                is_shortened=False,
                hops=0
            )

        current_url = url.strip()
        redirect_chain = [current_url]
        hops = 0
        is_shortened = False

        try:
            parsed = urlparse(current_url)
            host = (parsed.netloc or "").lower().split(":")[0]
            if host in SHORTENER_DOMAINS:
                is_shortened = True

            if not is_shortened:
                return ExpandedUrlResult(
                    original_url=url,
                    resolved_url=url,
                    redirect_chain=[url],
                    is_shortened=False,
                    hops=0
                )

            async with httpx.AsyncClient(
                follow_redirects=False,
                timeout=settings.SHORTENER_TIMEOUT_SECONDS
            ) as client:
                while hops < settings.SHORTENER_MAX_HOPS:
                    try:
                        resp = await client.head(current_url)
                        if resp.status_code in (301, 302, 303, 307, 308):
                            location = resp.headers.get("Location")
                            if location:
                                if location.startswith("/"):
                                    parsed_curr = urlparse(current_url)
                                    location = f"{parsed_curr.scheme}://{parsed_curr.netloc}{location}"
                                current_url = location
                                redirect_chain.append(current_url)
                                hops += 1
                            else:
                                break
                        else:
                            break
                    except httpx.HTTPError:
                        # Fallback to GET request if HEAD is blocked
                        try:
                            resp = await client.get(current_url)
                            if resp.status_code in (301, 302, 303, 307, 308):
                                location = resp.headers.get("Location")
                                if location:
                                    if location.startswith("/"):
                                        parsed_curr = urlparse(current_url)
                                        location = f"{parsed_curr.scheme}://{parsed_curr.netloc}{location}"
                                    current_url = location
                                    redirect_chain.append(current_url)
                                    hops += 1
                                else:
                                    break
                            else:
                                break
                        except Exception:
                            break
        except Exception as e:
            logger.debug(f"Shortener expansion for '{url}' encountered error: {e}")

        return ExpandedUrlResult(
            original_url=url,
            resolved_url=current_url,
            redirect_chain=redirect_chain,
            is_shortened=is_shortened,
            hops=hops
        )
