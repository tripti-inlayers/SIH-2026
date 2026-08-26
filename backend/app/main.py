from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

from app.config import settings
from app.core.logging import setup_logging, logger
from app.db.session import init_db
from app.api.v1 import health, analyze, reports

setup_logging()

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing SancharSaathi Backend Service...")
    init_db()
    yield

limiter = Limiter(key_func=get_remote_address, default_limits=["60/minute"])
app = FastAPI(
    title="SancharSaathi API",
    description="AI-Based Phishing and Social Engineering Detection Backend Service",
    version="1.0.0",
    lifespan=lifespan
)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

origins = [o.strip() for o in settings.CORS_ALLOWED_ORIGINS.split(",")] if settings.CORS_ALLOWED_ORIGINS != "*" else ["*"]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, prefix="/api/v1", tags=["health"])
app.include_router(analyze.router, prefix="/api/v1", tags=["analyze"])
app.include_router(reports.router, prefix="/api/v1", tags=["reports"])

@app.get("/")
async def root():
    return {"message": "SancharSaathi Security Engine active. See /api/v1/health"}
