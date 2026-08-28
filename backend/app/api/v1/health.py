import httpx
from fastapi import APIRouter
from app.schemas.reports import HealthResponse
from app.config import settings
from app.db.session import engine

router = APIRouter()

@router.get("/health", response_model=HealthResponse)
async def health_check():
    db_status = "connected" if engine is not None else "in_memory_fallback"
    
    ml_status = {"status": "unavailable", "details": "Failed to connect to ML service"}
    try:
        async with httpx.AsyncClient() as client:
            res = await client.get(f"{settings.ML_SERVICE_URL.rstrip('/')}/health", timeout=2.0)
            if res.status_code == 200:
                ml_data = res.json()
                ml_status = {"status": "ok", "mock_mode": ml_data.get("mock_mode", False), "service": "ml-service"}
    except Exception as e:
        ml_status = {"status": "unavailable", "details": str(e)}

    return HealthResponse(
        status="ok",
        database=db_status,
        threat_intel_provider=settings.THREAT_INTEL_PROVIDER,
        identity_provider=settings.IDENTITY_PROVIDER,
        version="1.0.0",
        ml_service=ml_status
    )
