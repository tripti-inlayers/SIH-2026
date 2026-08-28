from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from app.config import settings
from app.core.logging import logger
from app.db.models import Base
from typing import Optional

engine = None
AsyncSessionLocal = None

async def init_db():
    global engine, AsyncSessionLocal
    db_url = settings.DATABASE_URL or "sqlite+aiosqlite:///sancharsaathi.db"
    try:
        if db_url.startswith("postgresql://"):
            db_url = db_url.replace("postgresql://", "postgresql+asyncpg://", 1)
        engine = create_async_engine(db_url, echo=False, future=True)
        AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        logger.info(f"Database engine and tables initialized for {db_url}")
    except Exception as e:
        logger.warning(f"Failed to initialize DB engine ({e}); falling back to in-memory store.")
        engine = None
        AsyncSessionLocal = None
