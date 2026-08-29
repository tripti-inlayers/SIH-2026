import sqlite3
import json
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "sancharsaathi.db")

def init_db():
    conn = sqlite3.connect(DB_PATH, timeout=15.0)
    cursor = conn.cursor()
    cursor.execute("PRAGMA journal_mode=WAL;")
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content_hash TEXT NOT NULL,
            decision TEXT NOT NULL,
            risk_score REAL NOT NULL,
            signals_snapshot TEXT NOT NULL,
            report_type TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()
    conn.close()

def log_report(content_hash: str, decision: str, risk_score: float, signals: list, report_type: str):
    conn = sqlite3.connect(DB_PATH, timeout=15.0)
    cursor = conn.cursor()
    cursor.execute("PRAGMA journal_mode=WAL;")
    cursor.execute("""
        INSERT INTO reports (content_hash, decision, risk_score, signals_snapshot, report_type)
        VALUES (?, ?, ?, ?, ?)
    """, (content_hash, decision, risk_score, json.dumps(signals), report_type))
    conn.commit()
    conn.close()

# Initialize DB on module load
init_db()
