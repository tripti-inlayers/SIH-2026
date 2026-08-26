from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from sqlalchemy import String, Integer, Float, Boolean, JSON, DateTime, func, Text
import uuid
from datetime import datetime
from typing import Optional

class Base(DeclarativeBase):
    pass

class AnalysisModel(Base):
    __tablename__ = "analyses"

    analysis_id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    risk_score: Mapped[int] = mapped_column(Integer, nullable=False)
    risk_level: Mapped[str] = mapped_column(String(20), nullable=False)
    confidence: Mapped[float] = mapped_column(Float, nullable=False)
    detected_url: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    sender: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    reasons: Mapped[dict] = mapped_column(JSON, nullable=False)
    signals: Mapped[dict] = mapped_column(JSON, nullable=False)
    should_block: Mapped[bool] = mapped_column(Boolean, nullable=False)
    should_report: Mapped[bool] = mapped_column(Boolean, nullable=False)
    degraded: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    degraded_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    model_version: Mapped[str] = mapped_column(String(50), nullable=False)
    source: Mapped[str] = mapped_column(String(20), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

class ReportModel(Base):
    __tablename__ = "reports"

    report_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    analysis_id: Mapped[str] = mapped_column(String(36), nullable=False)
    threat_type: Mapped[str] = mapped_column(String(100), nullable=False)
    url_or_domain: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    risk_score: Mapped[int] = mapped_column(Integer, nullable=False)
    risk_level: Mapped[str] = mapped_column(String(20), nullable=False)
    evidence_summary: Mapped[dict] = mapped_column(JSON, nullable=False)
    submitted: Mapped[bool] = mapped_column(Boolean, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
