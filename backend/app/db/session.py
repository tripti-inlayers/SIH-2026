from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from app.config import settings
from app.core.logging import logger
from typing import Optional

engine = None
AsyncSessionLocal = None

def init_db():
    global engine, AsyncSessionLocal
    if settings.DATABASE_URL:
        try:
            db_url = settings.DATABASE_URL
            if db_url.startswith("postgresql://"):
                db_url = db_url.replace("postgresql://", "postgresql+asyncpg://", 1)
            engine = create_async_engine(db_url, echo=False, future=True)
            AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
            logger.info(f"Database engine initialized for {db_url}")
        except Exception as e:
            logger.warning(f"Failed to initialize DB engine ({e}); falling back to in-memory store.")
            engine = None
            AsyncSessionLocal = None
    else:
        logger.info("No DATABASE_URL configured — using in-memory repository fallback.")
