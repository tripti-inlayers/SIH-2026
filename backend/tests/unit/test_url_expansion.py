import pytest
from app.services.url_expansion import UrlExpanderService

@pytest.mark.asyncio
async def test_url_expander_non_shortener():
    expander = UrlExpanderService()
    res = await expander.expand("https://www.indiapost.gov.in")
    assert res.is_shortened is False
    assert res.resolved_url == "https://www.indiapost.gov.in"
    assert res.hops == 0

@pytest.mark.asyncio
async def test_url_expander_shortener_format():
    expander = UrlExpanderService()
    res = await expander.expand("https://bit.ly/non_existent_demo_link")
    assert res.is_shortened is True
    assert res.original_url == "https://bit.ly/non_existent_demo_link"
