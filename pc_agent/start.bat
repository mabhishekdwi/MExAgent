@echo off
:: ============================================================
:: MExAgent PC Agent
:: Starts Appium + ngrok and registers with the cloud backend
:: ============================================================

:: ── CONFIG — edit these ──────────────────────────────────────
set BACKEND_URL=https://your-app.onrender.com
set CONFIGURE_SECRET=
set NODE_PATH=C:\Program Files\nodejs
set APPIUM_PORT=4723
:: ─────────────────────────────────────────────────────────────

set PATH=%NODE_PATH%;%PATH%

echo.
echo  MExAgent PC Agent
echo  =================
echo.

:: Get device info via ADB
for /f "tokens=1" %%i in ('adb devices ^| findstr "device$"') do set DEVICE_UDID=%%i
if "%DEVICE_UDID%"=="" (
    echo  [!] No Android device found via ADB.
    echo      Connect your phone via USB with USB Debugging enabled.
    pause & exit /b 1
)
echo  [+] Device found: %DEVICE_UDID%

for /f "tokens=2 delims=: " %%a in ('adb shell getprop ro.product.model') do set DEVICE_NAME=%%a
for /f "tokens=2 delims=: " %%a in ('adb shell getprop ro.build.version.release') do set PLATFORM_VERSION=%%a
echo  [+] Model: %DEVICE_NAME%   Android: %PLATFORM_VERSION%

:: Start Appium in background
echo.
echo  [+] Starting Appium on port %APPIUM_PORT%...
start "Appium Server" /min cmd /c "appium server --base-path / --relaxed-security --port %APPIUM_PORT% > appium.log 2>&1"
timeout /t 3 /nobreak >nul

:: Start ngrok in background
echo  [+] Starting ngrok tunnel for port %APPIUM_PORT%...
start "ngrok" /min cmd /c "ngrok http %APPIUM_PORT% > nul 2>&1"
timeout /t 4 /nobreak >nul

:: Get ngrok public URL from its local API
for /f "delims=" %%u in ('powershell -Command "(Invoke-WebRequest -Uri 'http://localhost:4040/api/tunnels' -UseBasicParsing | ConvertFrom-Json).tunnels[0].public_url"') do set NGROK_URL=%%u

if "%NGROK_URL%"=="" (
    echo  [!] Could not get ngrok URL. Is ngrok installed?
    echo      Download from: https://ngrok.com/download
    pause & exit /b 1
)
echo  [+] ngrok URL: %NGROK_URL%

:: Register with the cloud backend
echo.
echo  [+] Registering with backend: %BACKEND_URL%
powershell -Command ^
  "$body = @{ appium_url='%NGROK_URL%'; device_udid='%DEVICE_UDID%'; device_name='%DEVICE_NAME%'; platform_version='%PLATFORM_VERSION%'; secret='%CONFIGURE_SECRET%' } | ConvertTo-Json; " ^
  "try { $r = Invoke-WebRequest -Uri '%BACKEND_URL%/configure' -Method POST -Body $body -ContentType 'application/json' -UseBasicParsing; Write-Host '  [+] Backend configured:' $r.Content } " ^
  "catch { Write-Host '  [!] Could not reach backend:' $_.Exception.Message }"

echo.
echo  ============================================================
echo   All systems running!
echo   Backend : %BACKEND_URL%
echo   Appium  : %NGROK_URL%  (-> localhost:%APPIUM_PORT%)
echo   Device  : %DEVICE_UDID%
echo  ============================================================
echo.
echo  Press Ctrl+C or close this window to stop everything.
echo.
pause
