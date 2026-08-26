# SancharSaathi System Architecture & Technical Specification

## 1. High-Level System Architecture

```
┌────────────────────────────────────────────────────────┐
│  ANDROID APP (Kotlin, Jetpack Compose, MVVM)          │
│                                                        │
│  ContentCaptureSource (SMS / Demo / Share-to-App)      │
│  → AnalysisRequest (local)                             │
│  → Retrofit API Layer                                  │
└───────────────────────────┬────────────────────────────┘
                            │ HTTPS / HTTP JSON
                            ▼
┌────────────────────────────────────────────────────────┐
│  FASTAPI BACKEND SERVICE                               │
│                                                        │
│  API Layer (routers /api/v1/analyze, /reports, /health)│
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
└───────────────────────────┴────────────────────────────┘
```

---

## 2. Subsystem Responsibilities

### Android Application (`/android`)
- **UI & Presentation**: Built with Jetpack Compose following Material 3 guidelines and single-activity MVVM architecture (`MainActivity` + Navigation Compose `NavHost`).
- **Capture Sources**:
  - `SmsCaptureSource`: Listens for `android.provider.Telephony.SMS_RECEIVED` broadcasts.
  - `DemoContentSource`: Emits the 3 fixed demo scenarios for local testing.
  - `SharedContentSource`: Captures text shared from other Android apps via `ACTION_SEND` intent filter.
- **State Management**: Every screen exposes a `StateFlow<UiState>` interface (`Loading`, `Success`, `Error`).
- **Resilience**: Operates on `Dispatchers.IO`, catches network exceptions (`IOException`, `SocketTimeoutException`), and maps them to clean user-facing error screens with manual retry buttons.

### FastAPI Backend (`/backend`)
- **API Layer**: Exposes `/api/v1/analyze`, `/api/v1/analyze/url`, `/api/v1/reports`, and `/api/v1/health`.
- **Analysis Orchestrator**: Executes 4 independent analyzer services concurrently via `asyncio.gather` with per-service timeout handling (`REQUEST_TIMEOUT_SECONDS=5.0`).
- **SSRF Prevention**: User URLs are never fetched server-side. All URL checks are strictly lexical or performed via safe domain metadata queries (RDAP).
- **Data Persistence**: SQLAlchemy async engine writing to Postgres/Supabase when `DATABASE_URL` is present, automatically falling back to thread-safe `InMemoryRepository` instances when unconfigured.

---

## 3. Risk Fusion Formula & Algorithm

The `RiskFusionEngine` combines triggered signals into a normalized risk score, level, confidence rating, and recommended action.

### 1. Raw Score Accumulation
$$\text{RawScore} = \sum_{s \in \text{Signals}, s.\text{triggered} = \text{True}} s.\text{weight}$$

### 2. Normalization & Clipping
$$\text{RiskScore} = \min\left(100, \max\left(0, \lfloor \text{RawScore} \times 100 \rceil \right)\right)$$

### 3. Risk Level Classification
$$\text{RiskLevel} = \begin{cases} 
\text{LOW} & \text{if } \text{RiskScore} \le \text{THRESHOLD\_LOW\_MAX} \quad (39) \\ 
\text{SUSPICIOUS} & \text{if } \text{RiskScore} \le \text{THRESHOLD\_SUSPICIOUS\_MAX} \quad (69) \\ 
\text{HIGH} & \text{if } \text{RiskScore} \ge 70 
\end{cases}$$

### 4. Confidence Score Calculation
$$\text{Confidence} = \min\left(0.95, 0.60 + 0.10 \times |\text{TriggeredCategories}|\right)$$
If `degraded = True` (any sub-service timed out), confidence is reduced:
$$\text{Confidence}_{\text{degraded}} = \max\left(0.30, \text{Confidence} - 0.20\right)$$

### 5. Action Logic
- **`should_block`**: Set to `True` iff $\text{RiskLevel} = \text{HIGH}$ and at least one URL was present in the request.
- **`should_report`**: Set to `True` iff $\text{RiskLevel} = \text{HIGH}$.
