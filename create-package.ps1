$out = "f:\MExAgent\MExAgent-Package"
$zip = "f:\MExAgent\MExAgent-Release.zip"

# Clean
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null
New-Item -ItemType Directory -Path "$out\PC-Agent" | Out-Null
New-Item -ItemType Directory -Path "$out\Android-APK" | Out-Null

# Copy files
Copy-Item "f:\MExAgent\pc_agent\dist\mexagent-pc.exe" "$out\PC-Agent\"
Copy-Item "f:\MExAgent\pc_agent\config.json"           "$out\PC-Agent\"
Copy-Item "f:\MExAgent\android\app\build\outputs\apk\debug\app-debug.apk" "$out\Android-APK\MExAgent.apk"

# Write README
@"
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
"@ | Set-Content "$out\README.txt"

# Zip
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path "$out\*" -DestinationPath $zip

Write-Host ""
Write-Host "Package created: $zip"
Write-Host ""
Get-Item $zip | Select-Object Name, @{N="Size (MB)";E={[math]::Round($_.Length/1MB,1)}}
