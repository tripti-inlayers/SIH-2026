import httpx
from fastapi import APIRouter
from app.schemas.reports import HealthResponse
from app.config import settings
from app.db import session

from app.services.orchestrator import AnalysisOrchestrator

router = APIRouter()
orchestrator = AnalysisOrchestrator()

@router.get("/health", response_model=HealthResponse)
async def health_check():
    eng = session.engine
    db_status = "persistent_sqlite" if (eng is not None and "sqlite" in str(eng.url)) else ("connected" if eng is not None else "in_memory_fallback")
    
    ml_status = {"status": "unavailable", "details": "Failed to connect to ML service"}
    try:
        async with httpx.AsyncClient() as client:
            res = await client.get(f"{settings.ML_SERVICE_URL.rstrip('/')}/health", timeout=2.0)
            if res.status_code == 200:
                ml_data = res.json()
                ml_status = {"status": "ok", "mock_mode": ml_data.get("mock_mode", False), "service": "ml-service"}
    except Exception as e:
        ml_status = {"status": "unavailable", "details": str(e)}

    # Perform live probe to verify PhishDestroy reachability
    threat_intel_probe = await orchestrator.threat_intel_provider.probe_health()
    threat_intel_status = {
        "reachable": threat_intel_probe.get("reachable", False),
        "provider": "phishdestroy",
        "details": threat_intel_probe.get("details", "Unavailable")
    }

    return HealthResponse(
        status="ok",
        database=db_status,
        threat_intel_provider="phishdestroy",
        identity_provider=settings.IDENTITY_PROVIDER,
        version="1.0.0",
        ml_service=ml_status,
        threat_intel=threat_intel_status
    )
