# SancharSaathi — AI-Based Phishing & Social Engineering Detection Assistant

**SancharSaathi** is an Android security assistant application and Python FastAPI backend that inspects incoming SMS messages and links, scores them for phishing and social engineering risk using multiple independent signals, explains risk factors in plain language, and blocks high-risk links before users can interact with them.

---

## Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│  ANDROID APP (Kotlin, Jetpack Compose, MVVM)          │
│                                                        │
│  ContentCaptureSource (SMS / Demo / Share-to-App)      │
│  → AnalysisRequest                                     │
│  → Retrofit API Layer                                  │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP / JSON (http://10.0.2.2:8000/)
                            ▼
┌────────────────────────────────────────────────────────┐
│  FASTAPI BACKEND SERVICE                               │
│                                                        │
│  API Routers (/api/v1/analyze, /reports, /health)      │
│  → AnalysisOrchestrator (asyncio.gather)               │
│     ├─ MessageAnalysisService (Rule-based NLP engine)  │
│     ├─ UrlAnalysisService (Lexical & lookalike engine) │
│     ├─ ThreatIntelService (RDAP / Mock abstraction)    │
│     └─ IdentityVerificationService (DLT Mock)          │
│  → RiskFusionEngine (0-100 score & level mapping)      │
│  → ReportService                                       │
│  → Repositories (Postgres DB / InMemory Fallback)      │
└───────────────────────────┬────────────────────────────┘
                            │ JSON Response
                            ▼
┌────────────────────────────────────────────────────────┐
│  ANDROID APP                                           │
│  RiskResult → UI State                                 │
│  → Warn / Verify / Block                               │
│  → Report Action (Threat Submission)                   │
└────────────────────────────────────────────────────────┘
```

---

## Key Features

- **Multi-Signal Phishing Risk Engine**: Evaluates message text, URL lexicals, domain lookalikes, threat intelligence, and sender identity concurrently.
- **Three-Tier Risk Levels**:
  - **LOW**: Muted green badge, calm confirmation ("Looks okay").
  - **SUSPICIOUS**: Amber badge, plain-text link display, independent verification guide.
  - **HIGH**: Red badge, automatic link blocking, interactive threat reporting flow.
- **Plain-Language Explanations**: Surfaces clear reasons without exposing internal jargon. Detailed technical breakdowns are accessible via the collapsible "View technical details" section.
- **Resilient Fallback**: Automatically degrades gracefully if any analyzer or DB service times out or is unreachable.

---

## Project Structure

```
SancharSaathi/
├── README.md
├── ARCHITECTURE.md
├── backend/
│   ├── app/
│   │   ├── api/v1/            # Health, Analyze, and Reports endpoints
│   │   ├── core/              # Logging, Exceptions, Security (SSRF protection)
│   │   ├── db/                # SQLAlchemy models & session initialization
│   │   ├── repositories/      # Analysis & Report repositories (DB + InMemory fallback)
│   │   ├── schemas/           # Pydantic models & DTOs
│   │   └── services/          # Message NLP, URL checks, Threat Intel, Identity, Fusion
│   ├── tests/                 # Unit, Integration, and E2E demo scenario test suites
│   ├── main.py                # FastAPI entry point
│   ├── config.py              # Environment configuration (pydantic-settings)
│   └── requirements.txt
└── android/
    ├── app/
    │   ├── src/main/java/com/sancharsaathi/app/
    │   │   ├── data/          # Remote DTOs, ApiService, Repositories, HistoryStore
    │   │   ├── di/            # Manual Dependency Injection (AppModule)
    │   │   ├── domain/        # Models, UseCases, ContentCaptureSources
    │   │   ├── presentation/  # Compose screens, components, viewmodels, theme, navigation
    │   │   ├── receiver/      # BroadcastReceiver for SMS_RECEIVED_ACTION
    │   │   └── permissions/   # SMS permission rationale & manager
    │   └── build.gradle.kts
    ├── build.gradle.kts
    └── settings.gradle.kts
```

---

## Environment & Configuration

### Backend Environment Variables (`/backend/.env`)

| Variable | Default Value | Description |
|---|---|---|
| `APP_ENV` | `development` | Environment name |
| `DATABASE_URL` | `""` | Postgres connection string (falls back to in-memory store if blank) |
| `CORS_ALLOWED_ORIGINS` | `*` | Allowed origins for CORS |
| `RISK_THRESHOLD_LOW_MAX` | `39` | Maximum score for LOW risk level |
| `RISK_THRESHOLD_SUSPICIOUS_MAX` | `69` | Maximum score for SUSPICIOUS risk level |
| `REQUEST_TIMEOUT_SECONDS` | `5.0` | Per-service timeout limit |
| `THREAT_INTEL_PROVIDER` | `mock` | Threat intel provider (`mock` or `rdap`) |
| `IDENTITY_PROVIDER` | `mock` | Identity provider (`mock`) |
| `REPORTING_PROVIDER` | `mock` | Threat reporting provider (`mock`) |
| `LOG_LEVEL` | `INFO` | Logger verbosity level |

---

## Quick Start & Installation

### 1. Running the FastAPI Backend

```bash
cd backend
python -m venv .venv
# On Windows PowerShell:
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
cp .env.example .env

# Run FastAPI server
python -m uvicorn app.main:app --reload --port 8000
```

Verify backend health:
```bash
curl http://127.0.0.1:8000/api/v1/health
```

### 2. Running Backend Tests

```bash
cd backend
python -m pytest
```

### 3. Building & Running the Android App

- Open `/android` in Android Studio.
- Ensure Android SDK 35 / compileSdk 35 is installed.
- Ensure JDK 17 or higher (such as Android Studio JBR) is configured.
- Run on Android Emulator (API 33+) or physical device connected to the same network.
- Execute Gradle build via command line:
  ```powershell
  cd android
  .\gradlew.bat assembleDebug
  .\gradlew.bat test
  ```

---

## Demo Mode Scenarios

The Android app features 3 built-in demo buttons on the Home screen that trigger real network round-trips to the backend:

1. **Scenario 1 — LOW RISK**:
   - *Message*: `"Hi, your order has been shipped and will arrive by Friday. Track here: https://www.indiapost.gov.in/track/12345"`
   - *Expected Outcome*: Risk Score ≤ 39, `LOW` risk badge, "Looks okay", no link blocking.
2. **Scenario 2 — SUSPICIOUS**:
   - *Message*: `"Your package could not be delivered. Please confirm your address within 24 hours: http://track-parcel-update.tk/confirm"`
   - *Expected Outcome*: Risk Score 40–69, `SUSPICIOUS` amber badge, non-clickable URL, "Verify before you act" guide.
3. **Scenario 3 — HIGH RISK**:
   - *Message*: `"URGENT: Your bank account will be suspended. Verify your PIN immediately to avoid blocking: http://secure-bank0findia-verify.xyz/login"`
   - *Expected Outcome*: Risk Score ≥ 70, `HIGH` red badge, **Link Blocked** screen, plain-text link, interactive threat reporting flow creating a `reportId`.

---

## Mocked vs. Proposed External Integrations

To ensure privacy and demonstrate end-to-end functionality without requiring paid enterprise API keys, the following external providers are abstracted and mocked:

1. **Threat Intelligence Service**:
   - *Implemented*: Deterministic mock provider matching demo scenarios + real keyless RDAP lookup (`https://rdap.org/domain/{domain}`) via `httpx`.
   - *Real Integration Requirements*: Enterprise API credentials for Google Web Risk API, OpenPhish, or PhishTank (stubs provided in `mock_provider.py`).
2. **Identity & TRAI DLT Sender Registry**:
   - *Implemented*: Heuristic mock engine checking sender headers against standard Indian DLT principal-entity patterns (`AX-INDPOST`, `VK-SBIINB`) and sender/claimed-organization mismatches.
   - *Real Integration Requirements*: Official telecom DLT API integration (restricted to licensed entity queries in India).
3. **Threat Reporting Service**:
   - *Implemented*: Mock reporting service generating unique `RPT-XXXXXXXX` IDs stamped with `"Proposed Reporting Integration — demonstration only."`.
   - *Real Integration Requirements*: Integration with national cybercrime reporting portals (e.g. DoT Chakshu portal API).

---

## Known Limitations

- **SMS Default App Platform Restriction**: Modern Android versions enforce strict background SMS delivery policies. Background scanning via `RECEIVE_SMS` broadcast receiver works when permissions are granted, but default SMS app status is restricted by Google Play policy. **Demo Mode** and **Share-to-App** (`ACTION_SEND` intent filter) provide guaranteed, reliable demonstration flows.
- **SSRF Safety**: Server-side URL fetching of user-submitted URLs is intentionally disabled. All URL analysis is performed lexically or via safe domain lookups (RDAP).
- **Language Pattern Coverage**: The rule engine includes common English and Hindi/Hinglish urgency patterns (e.g., *turant*, *account band ho jayega*), but does not replace full multilingual neural transformer NLP models.

---

## Manual Verification Checklist

1. Launch backend (`python -m uvicorn app.main:app --port 8000`) and verify `GET /api/v1/health` returns HTTP 200.
2. Run backend pytest suite (`python -m pytest`) and confirm all 15 tests pass.
3. Run Android unit tests (`.\gradlew.bat test`) and assemble debug APK (`.\gradlew.bat assembleDebug`).
4. Execute Low, Suspicious, and High Risk demo scenarios from the Android app against the running backend.
5. Terminate the backend server and retry a scenario in the app to verify the fallback "Full security analysis is currently unavailable" screen.
