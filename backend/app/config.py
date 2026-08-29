from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    APP_ENV: str = "development"
    DATABASE_URL: Optional[str] = None
    SUPABASE_URL: Optional[str] = None
    SUPABASE_KEY: Optional[str] = None
    CORS_ALLOWED_ORIGINS: str = "*"
    RISK_THRESHOLD_LOW_MAX: int = 39
    RISK_THRESHOLD_SUSPICIOUS_MAX: int = 69
    REQUEST_TIMEOUT_SECONDS: float = 5.0
    THREAT_INTEL_TIMEOUT_SECONDS: float = 3.0
    SHORTENER_TIMEOUT_SECONDS: float = 2.0
    SHORTENER_MAX_HOPS: int = 5
    URL_CACHE_TTL_SECONDS: int = 3600
    THREAT_INTEL_PROVIDER: str = "multi"
    GOOGLE_SAFE_BROWSING_API_KEY: Optional[str] = None
    GOOGLE_WEBRISK_API_KEY: Optional[str] = None
    PHISHTANK_API_KEY: Optional[str] = None
    IDENTITY_PROVIDER: str = "mock"
    REPORTING_PROVIDER: str = "mock"
    LOG_LEVEL: str = "INFO"
    ML_SERVICE_URL: str = "http://localhost:8001"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

settings = Settings()
