import httpx
from typing import Optional
from app.schemas.common import RiskSignal
from app.config import settings
from app.core.logging import logger

class MlAnalysisService:
    async def analyze(self, text: str) -> Optional[RiskSignal]:
        if not text or not text.strip():
            return None
            
        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    f"{settings.ML_SERVICE_URL.rstrip('/')}/predict",
                    json={"message": text},
                    timeout=settings.REQUEST_TIMEOUT_SECONDS
                )
                response.raise_for_status()
                data = response.json()
                
                prediction = data.get("prediction")
                confidence = data.get("confidence", 0.0)
                
                if prediction == 1:
                    return RiskSignal(
                        category="ml_model",
                        code="AI_SPAM_DETECTED",
                        description="AI model flagged this message as spam/phishing.",
                        technical_detail=f"RoBERTa sequence classification confidence: {confidence:.2f}",
                        weight=0.50, # Heavily trust finetuned AI detection
                        triggered=True
                    )
                else:
                    return RiskSignal(
                        category="ml_model",
                        code="AI_NORMAL",
                        description="AI model found no spam indicators.",
                        technical_detail=f"RoBERTa sequence classification confidence: {confidence:.2f}",
                        weight=0.0,
                        triggered=False
                    )
        except httpx.TimeoutException as e:
            logger.error(f"ML analysis service timed out: {e}")
            raise
        except Exception as e:
            logger.error(f"ML analysis service failed: {e}")
            raise
