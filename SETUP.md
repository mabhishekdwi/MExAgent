# MExAgent — Setup Guide

---

## How It Works

```text
Your Phone
    │  (WiFi)
    ▼
Render (Cloud Backend)  ──── ngrok tunnel ────►  Your PC (Appium)
    │                                                    │
    │                                               USB/WiFi ADB
    │                                                    ▼
    └────────────────────────────────────────────► Android Phone
```

- **Cloud backend** (Render) — always online, no PC needed for this
- **PC** — only needs to run Appium + ngrok when you want to test
- **Phone** — connects to Render URL, works from any network

---

## Why ngrok

Your PC has a private IP (`192.168.1.x`) that the internet cannot reach.
Render (cloud) needs to call Appium on your PC — but your router blocks it.

ngrok creates a public tunnel:

```text
Render  →  ngrok.com  →  your router  →  your PC :4723 (Appium)
          (public URL)    (private IP)
```

Without ngrok, Render simply cannot reach your PC.
The start script handles ngrok automatically — you don't touch it manually.

---

## One-Time Setup

### Step 1 — Deploy Backend to Render (free)

1. Push this repo to GitHub
2. Go to **render.com** → New → Web Service → connect your repo
3. Set these in the Render dashboard:
   - **Root Directory**: `backend`
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
4. Add environment variables:

```text
LLM_PROVIDER        = groq
GROQ_API_KEY        = your_key_here     ← get free at console.groq.com
ACTION_DELAY_MS     = 2500
CONFIGURE_SECRET    = pick_any_password ← optional, protects /configure
```

1. Deploy — you get a URL like `https://mexagent-backend.onrender.com`

---

### Step 2 — Install Appium on Your PC

```bash
npm install -g appium
appium driver install uiautomator2
```

---

### Step 3 — Install ngrok on Your PC

1. Download from **ngrok.com/download**
2. Sign up free and run:

```bash
ngrok config add-authtoken YOUR_TOKEN
```

---

### Step 4 — Install the Android App

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Open MExAgent → Settings → set Backend URL to your Render URL:

```text
https://mexagent-backend.onrender.com
```

---

## Every Time You Want to Test

### On Your PC — one command

Edit `pc_agent/config.json` once:

```json
{
  "backendUrl": "https://your-app.onrender.com",
  "configureSecret": "",
  "appiumPort": 4723
}
```

Then every time:

```bash
cd pc_agent
npm start
```

That's it. The script automatically:

- Detects your phone via ADB
- Starts Appium server
- Creates ngrok tunnel
- Registers the ngrok URL with the cloud backend

### On Your Phone

1. Open any app you want to test
2. Tap the MExAgent floating panel → expand → press **▶ Start**
3. Watch the agent crawl with visual highlights

---

## Connection Check

In the app: **Settings → Test Connection** shows:

| Dot | What it checks |
| --- | --- |
| Backend | Your Render service is reachable |
| Appium | PC script is running and registered |
| Device | Phone detected by Appium |

All three should be green before pressing Start.

---

## Status Reference

| Status | Meaning |
| --- | --- |
| **Idle** | Ready — press ▶ |
| **Stopped** | Last session ended — press ▶ to run again, this is normal |
| **Running** | Agent is crawling |
| **Error** | Check that PC script is running and all 3 connection dots are green |

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Appium: ✗ in connection check | Run `start.bat` / `start.sh` on your PC |
| Backend unreachable | Render free tier sleeps after 15 min — wait ~30s for it to wake |
| Device not found | Plug phone via USB, accept USB debugging prompt, re-run start script |
| ngrok URL changes | Free ngrok gives a new URL each restart — the start script re-registers it automatically |
