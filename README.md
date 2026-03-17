# MExAgent — Mobile Exploratory Testing Agent

An autonomous Android QA agent that behaves like a human tester.
**Think → Plan → Execute**

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    ANDROID DEVICE / EMULATOR                    │
│                                                                 │
│  ┌─────────────────────┐        ┌──────────────────────────┐   │
│  │   App Under Test    │        │   MExAgent Android App   │   │
│  │  (any Android app)  │        │                          │   │
│  │                     │        │  ┌────────────────────┐  │   │
│  │  Appium controls    │        │  │  Floating Overlay  │  │   │
│  │  this app via       │        │  │  [Start][Stop]     │  │   │
│  │  UiAutomator2       │        │  │  [Logs][Settings]  │  │   │
│  │                     │        │  └────────────────────┘  │   │
│  └─────────────────────┘        │  ┌────────────────────┐  │   │
│                                 │  │  Settings Screen   │  │   │
│                                 │  │  • Backend URL     │  │   │
│                                 │  │  • Depth (1-10)    │  │   │
│                                 │  │  • AI Mode ON/OFF  │  │   │
│                                 │  └────────────────────┘  │   │
│                                 │  ┌────────────────────┐  │   │
│                                 │  │    Log Viewer      │  │   │
│                                 │  │  PASS / FAIL / ACT │  │   │
│                                 │  └────────────────────┘  │   │
│                                 └──────────┬───────────────┘   │
└────────────────────────────────────────────┼────────────────────┘
                              HTTP REST API  │  POST /start
                              (WiFi / USB)   │  POST /stop
                                             │  GET  /status
                                             │  GET  /logs
                                             ▼
┌─────────────────────────────────────────────────────────────────┐
│               BACKEND SERVER  (FastAPI · Python)                │
│                                                                 │
│   POST /start ──► AgentController                              │
│                        │                                        │
│                        ▼                                        │
│              ┌─────────────────┐                               │
│              │  Explorer Loop  │  ◄── Think → Plan → Execute   │
│              └────────┬────────┘                               │
│                       │                                         │
│           ┌───────────┼───────────┐                            │
│           ▼           ▼           ▼                            │
│     ┌──────────┐ ┌─────────┐ ┌──────────┐                     │
│     │  Appium  │ │   XML   │ │   LLM    │                     │
│     │  Driver  │ │ Parser  │ │  Client  │                     │
│     │ Manager  │ │         │ │          │                     │
│     └────┬─────┘ └────┬────┘ └────┬─────┘                     │
│          │            │           │                             │
│    get_driver()  ScreenContext  TestPlan                        │
│                                   │                             │
│                              Groq API  ──► llama3-70b           │
│                            OR Ollama   ──► llama3 (local)       │
│                                                                 │
│   GET /logs ──► LogStore (in-memory, polled every 2s)          │
└──────────────────────────┬──────────────────────────────────────┘
                           │  WebDriver Protocol
                           │  (localhost:4723)
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   APPIUM SERVER  (Node.js)                      │
│                                                                 │
│   UiAutomator2 driver                                           │
│   • get page_source  → XML hierarchy                           │
│   • click(element)   ← tap                                     │
│   • send_keys(text)  ← type                                    │
│   • swipe(coords)    ← scroll                                  │
│   • back()           ← navigate back                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Think → Plan → Execute Flow

```
User taps START in overlay
        │
        ▼
Backend: POST /start  ──► start_exploration(depth=2, ai_mode=true)
        │
        ▼
┌──────────────────────────────────────────────────────┐
│                  THINK (Observe)                     │
│  1. driver.page_source → raw XML (3000+ nodes)       │
│  2. xml_parser strips invisible / irrelevant nodes   │
│  3. ScreenContext: [{type, label, clickable}, ...]   │
└───────────────────┬──────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────┐
│                  PLAN (Reason)                       │
│  4. LLM receives compact JSON screen description     │
│  5. Returns:                                         │
│     {                                                │
│       "screen_type": "login",                        │
│       "goal": "Authenticate user",                   │
│       "test_plan": [                                 │
│         {"action":"type","target":"Username",        │
│          "value":"testuser"},                        │
│         {"action":"type","target":"Password",        │
│          "value":"Password123!"},                    │
│         {"action":"tap","target":"Login"}            │
│       ]                                              │
│     }                                                │
└───────────────────┬──────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────┐
│                 EXECUTE (Act)                        │
│  6. For each action:                                 │
│     a. Find element (resource-id → a11y-id → text)  │
│     b. Execute via Appium                            │
│     c. Log PASS / FAIL to store                     │
│     d. Detect navigation → recurse if depth left    │
│     e. Press Back → continue parent actions         │
└──────────────────────────────────────────────────────┘
        │
        ▼
Mobile app polls GET /logs every 2s → displays in LogActivity
```

---

## Sample Log Output (Login Screen)

```
[10:23:01.042] [INFO] === MExAgent started | session=a1b2c3d4 | depth=2 | ai=True ===
[10:23:01.890] [INFO] [Depth 1/2] Exploring: Login
[10:23:02.010] [PASS] Input visible: "Username"
[10:23:02.011] [PASS] Input visible: "Password"
[10:23:02.012] [PASS] Button visible: "Login"
[10:23:02.013] [PASS] Link visible: "Forgot Password"
[10:23:02.014] [INFO] Generating AI test plan...
[10:23:03.891] [INFO] Goal: Authenticate user  (4 actions)
[10:23:03.892] [ACT]  TYPE "Username" = "testuser"
[10:23:04.712] [PASS] Typed 'testuser' into 'Username'
[10:23:04.713] [ACT]  TYPE "Password" = "Password123!"
[10:23:05.533] [PASS] Typed 'Password123!' into 'Password'
[10:23:05.534] [ACT]  TAP "Login"
[10:23:06.354] [PASS] Tapped 'Login'
[10:23:06.355] [PASS] Navigated to new screen: .DashboardActivity
[10:23:06.356] [INFO] [Depth 2/2] Exploring: Dashboard
[10:23:07.121] [PASS] Button visible: "Profile"
[10:23:07.122] [PASS] Button visible: "Settings"
[10:23:07.123] [PASS] Button visible: "Logout"
...
[10:23:15.001] [INFO] === Exploration complete | 12 actions ===
```

---

## Project Structure

```
MExAgent/
├── android/                     ← Android app (Kotlin)
│   └── app/src/main/
│       ├── java/com/mexagent/app/
│       │   ├── main/            ← MainActivity + ViewModel
│       │   ├── overlay/         ← OverlayService (floating button)
│       │   ├── settings/        ← SettingsActivity + DataStore
│       │   ├── logs/            ← LogActivity + Adapter + ViewModel
│       │   ├── agent/           ← AgentController + State
│       │   └── network/         ← Retrofit ApiClient + models
│       └── res/layout/          ← XML layouts
│
├── backend/                     ← FastAPI backend (Python)
│   └── app/
│       ├── main.py              ← FastAPI app entry point
│       ├── config/settings.py   ← Pydantic settings (.env)
│       ├── api/v1/endpoints/    ← /start /stop /status /logs /health
│       ├── appium/              ← driver_manager + action_executor
│       ├── parser/xml_parser.py ← XML → ScreenContext
│       ├── llm/                 ← Groq / Ollama client + prompts
│       ├── crawler/explorer.py  ← Core Think→Plan→Execute loop
│       ├── schemas/             ← Pydantic models
│       └── utils/logger.py      ← In-memory log store
│
└── appium/
    ├── scripts/check_environment.py  ← Pre-flight checker
    └── scripts/start_appium.sh       ← Start Appium server
```

---

## Setup Guide

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java JDK | 11+ | `brew install openjdk` / adoptopenjdk.net |
| Android SDK | API 26+ | Android Studio |
| Node.js | 18+ | nodejs.org |
| Appium | 2.x | `npm install -g appium` |
| UiAutomator2 | latest | `appium driver install uiautomator2` |
| Python | 3.11+ | python.org |

---

### Step 1 — Start Appium Server

```bash
appium server --port 4723 --base-path / --relaxed-security
```

Or use the helper script:
```bash
bash appium/scripts/start_appium.sh
```

Verify: `http://localhost:4723/status` → `{"value":{"ready":true}}`

---

### Step 2 — Configure Backend

```bash
cd backend
cp .env.example .env
# Edit .env — set GROQ_API_KEY (free at console.groq.com)
# Or set LLM_PROVIDER=ollama if using local Ollama

pip install -r requirements.txt
python app/main.py
```

API docs: `http://localhost:8000/docs`

Run pre-flight check:
```bash
python appium/scripts/check_environment.py
```

---

### Step 3 — Build Android App

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio and run directly.

---

### Step 4 — Run

1. Open **MExAgent** on your Android device
2. Tap **Settings** → enter your backend IP (e.g. `http://192.168.1.10:8000`)
3. Set **Depth** (2 recommended) and enable **AI Mode**
4. Tap **Show Floating Control** → grant overlay permission
5. **Open the app you want to test**
6. Tap the green **▶ Start** button in the floating overlay
7. Watch **Logs** for real-time PASS / FAIL / ACT entries

---

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| POST | `/start` | Start exploration. Body: `{depth, ai_mode, package_name}` |
| POST | `/stop` | Stop exploration |
| GET | `/status` | Current agent state + screen + action count |
| GET | `/logs` | Fetch logs. Query: `since_id`, `session_id`, `limit` |
| DELETE | `/logs` | Clear log store |
| GET | `/health` | Liveness + Appium connectivity |
| GET | `/docs` | Interactive Swagger UI |

---

## LLM Providers

### Groq (recommended — free tier available)
1. Create account at `console.groq.com`
2. Generate API key
3. Set in `.env`: `GROQ_API_KEY=gsk_...` and `LLM_PROVIDER=groq`

### Ollama (fully local, no API key)
1. Install: `curl -fsSL https://ollama.ai/install.sh | sh`
2. Pull model: `ollama pull llama3`
3. Set in `.env`: `LLM_PROVIDER=ollama`

---

## Configuration

| `.env` Key | Default | Description |
|------------|---------|-------------|
| `APPIUM_URL` | `http://localhost:4723` | Appium server URL |
| `LLM_PROVIDER` | `groq` | `groq` or `ollama` |
| `GROQ_API_KEY` | — | Required if using Groq |
| `DEFAULT_DEPTH` | `2` | How many screens deep to explore |
| `MAX_ACTIONS_PER_SCREEN` | `10` | Max actions per screen (heuristic mode) |
| `ACTION_DELAY_MS` | `800` | Wait between actions (ms) |
| `PORT` | `8000` | Backend server port |

---

## Extending

**Add a new LLM provider**: implement `_call_<provider>()` in `backend/app/llm/llm_client.py`

**Custom test strategies**: modify `SYSTEM_PROMPT` in `backend/app/llm/prompt_builder.py`

**Persist logs to disk**: swap `_log_store: List` in `utils/logger.py` with SQLite writes

**Multiple device support**: instantiate multiple `DriverManager` instances, one per device UDID
