#!/usr/bin/env bash
# Start Appium server with UiAutomator2 driver on port 4723
# Usage: bash start_appium.sh [--port 4723]

PORT=${1:-4723}

echo "Starting Appium server on port $PORT ..."
appium server \
  --port "$PORT" \
  --base-path "/" \
  --log-level info \
  --relaxed-security \
  --allow-cors
