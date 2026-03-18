#!/bin/bash
# ============================================================
# MExAgent PC Agent
# Starts Appium + ngrok and registers with the cloud backend
# ============================================================

# ── CONFIG — edit these ──────────────────────────────────────
BACKEND_URL="https://your-app.onrender.com"
CONFIGURE_SECRET=""
APPIUM_PORT=4723
# ─────────────────────────────────────────────────────────────

echo ""
echo " MExAgent PC Agent"
echo " ================="
echo ""

# Get device info
DEVICE_UDID=$(adb devices | grep -v "List" | grep "device" | awk '{print $1}' | head -1)
if [ -z "$DEVICE_UDID" ]; then
    echo " [!] No Android device found. Connect via USB with USB Debugging on."
    exit 1
fi
DEVICE_NAME=$(adb shell getprop ro.product.model | tr -d '\r')
PLATFORM_VERSION=$(adb shell getprop ro.build.version.release | tr -d '\r')
echo " [+] Device: $DEVICE_UDID | $DEVICE_NAME | Android $PLATFORM_VERSION"

# Start Appium in background
echo " [+] Starting Appium on port $APPIUM_PORT..."
appium server --base-path / --relaxed-security --port $APPIUM_PORT > /tmp/appium.log 2>&1 &
APPIUM_PID=$!
sleep 3

# Start ngrok in background
echo " [+] Starting ngrok tunnel..."
ngrok http $APPIUM_PORT > /dev/null 2>&1 &
NGROK_PID=$!
sleep 4

# Get ngrok URL
NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | python3 -c "import sys,json; print(json.load(sys.stdin)['tunnels'][0]['public_url'])" 2>/dev/null)
if [ -z "$NGROK_URL" ]; then
    echo " [!] Could not get ngrok URL. Install ngrok: https://ngrok.com/download"
    kill $APPIUM_PID 2>/dev/null
    exit 1
fi
echo " [+] ngrok URL: $NGROK_URL"

# Register with cloud backend
echo " [+] Registering with backend: $BACKEND_URL"
curl -s -X POST "$BACKEND_URL/configure" \
  -H "Content-Type: application/json" \
  -d "{\"appium_url\":\"$NGROK_URL\",\"device_udid\":\"$DEVICE_UDID\",\"device_name\":\"$DEVICE_NAME\",\"platform_version\":\"$PLATFORM_VERSION\",\"secret\":\"$CONFIGURE_SECRET\"}" \
  | python3 -m json.tool 2>/dev/null || echo "  (could not parse response)"

echo ""
echo " ============================================================"
echo "  All systems running!"
echo "  Backend : $BACKEND_URL"
echo "  Appium  : $NGROK_URL  (-> localhost:$APPIUM_PORT)"
echo "  Device  : $DEVICE_UDID"
echo " ============================================================"
echo ""
echo " Press Ctrl+C to stop everything."

# Keep running, cleanup on exit
trap "kill $APPIUM_PID $NGROK_PID 2>/dev/null; echo 'Stopped.'" EXIT
wait
