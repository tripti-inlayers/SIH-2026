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
    THREAT_INTEL_PROVIDER: str = "mock"
    IDENTITY_PROVIDER: str = "mock"
    REPORTING_PROVIDER: str = "mock"
    LOG_LEVEL: str = "INFO"
    ML_SERVICE_URL: str = "http://localhost:8001"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

settings = Settings()
