from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    APP_ENV: str = "development"
    DATABASE_URL: Optional[str] = None
    SUPABASE_URL: Optional[str] = None
    SUPABASE_KEY: Optional[str] = None
    CORS_ALLOWED_ORIGINS: str = "*"
    RISK_THRESHOLD_LOW_MAX: int = 49
    RISK_THRESHOLD_SUSPICIOUS_MAX: int = 79
    REQUEST_TIMEOUT_SECONDS: float = 5.0
    THREAT_INTEL_PROVIDER: str = "google_webrisk"
    GOOGLE_WEBRISK_API_KEY: Optional[str] = None
    IDENTITY_PROVIDER: str = "mock"
    REPORTING_PROVIDER: str = "mock"
    LOG_LEVEL: str = "INFO"
    ML_SERVICE_URL: str = "http://127.0.0.1:8001"

    model_config = SettingsConfigDict(
        env_file=(".env", "backend/.env", "../backend/.env"),
        env_file_encoding="utf-8",
        extra="ignore"
    )

settings = Settings()
