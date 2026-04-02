MExAgent — Quick Start
======================

STEP 1 — Install APK on Android phone
  - Copy MExAgent.apk from Android-APK\ to your phone
  - Install it (allow unknown sources if asked)

STEP 2 — Set up PC Agent
  - Go to PC-Agent\ folder
  - Open config.json and make sure backendUrl is set:
      "backendUrl": "https://mexagent.onrender.com"
  - Connect your Android phone via USB
  - On phone: Settings > Developer Options > USB Debugging ON
  - Double-click mexagent-pc.exe

STEP 3 — Start the agent
  - Open MExAgent on your phone
  - Go to Settings > set Backend URL to: https://mexagent.onrender.com
  - Tap Test Connection — all 3 dots should be green
  - Press the floating ▶ Start button

LOGS
  - Live logs appear in the app (tap the clipboard icon)
  - Log file saved next to mexagent-pc.exe as mexagent-logs.txt

REQUIREMENTS
  - Windows PC with USB port
  - Android phone (Android 8+) with USB Debugging enabled
  - Internet connection
