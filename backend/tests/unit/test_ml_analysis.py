import pytest
from unittest.mock import patch, MagicMock, AsyncMock
import httpx
from app.services.ml_analysis import MlAnalysisService
from app.schemas.common import RiskSignal

@pytest.fixture
def ml_service():
    return MlAnalysisService()

@pytest.mark.asyncio
async def test_ml_prediction_1(ml_service):
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_response = MagicMock()
        mock_response.json.return_value = {"prediction": 1, "label": "spam", "confidence": 0.95}
        mock_response.raise_for_status.return_value = None
        mock_post.return_value = mock_response

        signal = await ml_service.analyze("URGENT: Suspended account")
        assert signal is not None
        assert signal.triggered is True
        assert signal.code == "AI_SPAM_DETECTED"
        assert signal.weight == 0.35

@pytest.mark.asyncio
async def test_ml_prediction_0(ml_service):
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_response = MagicMock()
        mock_response.json.return_value = {"prediction": 0, "label": "ham", "confidence": 0.8}
        mock_response.raise_for_status.return_value = None
        mock_post.return_value = mock_response

        signal = await ml_service.analyze("Hello, are we meeting today?")
        assert signal is not None
        assert signal.triggered is False
        assert signal.code == "AI_NORMAL"
        assert signal.weight == 0.0

@pytest.mark.asyncio
async def test_ml_service_unavailable(ml_service):
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_post.side_effect = httpx.ConnectError("Connection refused")
        
        with pytest.raises(httpx.ConnectError):
            await ml_service.analyze("Any text")

@pytest.mark.asyncio
async def test_ml_service_timeout(ml_service):
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_post.side_effect = httpx.TimeoutException("Read timeout")
        
        with pytest.raises(httpx.TimeoutException):
            await ml_service.analyze("Any text")

@pytest.mark.asyncio
async def test_invalid_ml_response(ml_service):
    with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
        mock_response = MagicMock()
        mock_response.json.side_effect = ValueError("Invalid JSON")
        mock_response.raise_for_status.return_value = None
        mock_post.return_value = mock_response

        with pytest.raises(ValueError):
            await ml_service.analyze("Any text")
