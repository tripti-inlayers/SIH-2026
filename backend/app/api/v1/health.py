from fastapi import APIRouter
from app.schemas.reports import HealthResponse
from app.config import settings
from app.db.session import engine

router = APIRouter()

@router.get("/health", response_model=HealthResponse)
async def health_check():
    db_status = "connected" if engine is not None else "in_memory_fallback"
    return HealthResponse(
        status="ok",
        database=db_status,
        threat_intel_provider=settings.THREAT_INTEL_PROVIDER,
        identity_provider=settings.IDENTITY_PROVIDER,
        version="1.0.0"
    )
