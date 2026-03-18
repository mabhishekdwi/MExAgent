#!/usr/bin/env node
/**
 * MExAgent PC Agent — single command setup
 * npm start
 *
 * Automatically:
 *   - Installs Appium if missing
 *   - Installs uiautomator2 driver if missing
 *   - Detects connected Android device via ADB
 *   - Starts Appium server
 *   - Creates public tunnel via localtunnel
 *   - Registers tunnel URL + device info with cloud backend
 */

const { execSync, spawnSync, spawn } = require("child_process");
const https = require("https");
const fs = require("fs");
const path = require("path");

// ── Load config ───────────────────────────────────────────────────────────────
const configPath = path.join(__dirname, "config.json");
if (!fs.existsSync(configPath)) {
  console.error("❌  config.json not found.");
  process.exit(1);
}
const config = JSON.parse(fs.readFileSync(configPath, "utf8"));
const { backendUrl, configureSecret, appiumPort = 4723 } = config;

if (!backendUrl || backendUrl.includes("your-app")) {
  console.error("❌  Edit config.json and set your backend URL first.");
  process.exit(1);
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function run(cmd, opts = {}) {
  return execSync(cmd, { encoding: "utf8", ...opts }).trim();
}

function tryRun(cmd) {
  try { return run(cmd); } catch { return null; }
}

function spawnBackground(cmd, args, label) {
  const proc = spawn(cmd, args, {
    stdio: ["ignore", "pipe", "pipe"],
    shell: process.platform === "win32",
    detached: false,
  });
  proc.stdout.on("data", (d) => process.stdout.write(`[${label}] ${d}`));
  proc.stderr.on("data", (d) => process.stderr.write(`[${label}] ${d}`));
  proc.on("exit", (code) => {
    if (code !== 0 && code !== null)
      console.log(`[${label}] exited with code ${code}`);
  });
  return proc;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function httpPost(url, body) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(body);
    const parsed = new URL(url);
    const req = https.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || 443,
        path: parsed.pathname,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(payload),
        },
      },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => resolve(data));
      }
    );
    req.on("error", reject);
    req.write(payload);
    req.end();
  });
}

// ── Auto-install Appium if missing ────────────────────────────────────────────
function ensureAppium() {
  const version = tryRun("appium --version");
  if (version) {
    console.log(` ✓ Appium  : already installed (v${version})`);
    return;
  }
  console.log(" ⟳ Appium not found — installing globally (npm install -g appium)...");
  try {
    run("npm install -g appium", { stdio: "inherit" });
    console.log(" ✓ Appium  : installed");
  } catch (e) {
    console.error(" ✗ Failed to install Appium:", e.message);
    console.error("   Try manually: npm install -g appium");
    process.exit(1);
  }
}

function ensureUiautomator2() {
  const list = tryRun("appium driver list --installed");
  if (list && list.includes("uiautomator2")) {
    console.log(" ✓ Driver  : uiautomator2 already installed");
    return;
  }
  console.log(" ⟳ Installing uiautomator2 driver...");
  try {
    run("appium driver install uiautomator2", { stdio: "inherit" });
    console.log(" ✓ Driver  : uiautomator2 installed");
  } catch (e) {
    console.error(" ✗ Failed to install uiautomator2:", e.message);
    process.exit(1);
  }
}

// ── Main ──────────────────────────────────────────────────────────────────────
(async () => {
  console.log("\n MExAgent PC Agent\n ==================\n");

  // 1. Ensure Appium + driver installed
  ensureAppium();
  ensureUiautomator2();

  // 2. Detect device
  let deviceUdid = "", deviceName = "", platformVersion = "";
  try {
    const devices = run("adb devices");
    const match = devices.split("\n").find((l) => l.includes("\tdevice"));
    if (!match) throw new Error("no device");
    deviceUdid = match.split("\t")[0].trim();
    deviceName = run("adb shell getprop ro.product.model").replace(/\r/g, "");
    platformVersion = run("adb shell getprop ro.build.version.release").replace(/\r/g, "");
    console.log(` ✓ Device  : ${deviceName} | Android ${platformVersion} | ${deviceUdid}`);
  } catch {
    console.error(" ✗ No Android device found via ADB.");
    console.error("   Connect phone via USB and enable USB Debugging.");
    process.exit(1);
  }

  // 3. Start Appium
  console.log(` ⟳ Starting Appium on port ${appiumPort}...`);
  const appium = spawnBackground(
    "appium",
    ["server", "--base-path", "/", "--relaxed-security", "--port", String(appiumPort)],
    "Appium"
  );
  await sleep(3000);
  console.log(` ✓ Appium  : running on port ${appiumPort}`);

  // 4. Start localtunnel
  console.log(` ⟳ Starting tunnel...`);
  let lt;
  let tunnelUrl = "";
  try {
    const localtunnel = require("localtunnel");
    lt = await localtunnel({ port: appiumPort });
    tunnelUrl = lt.url;
    lt.on("error", (err) => console.error("[tunnel] error:", err.message));
    lt.on("close", () => console.log("[tunnel] closed"));
  } catch (e) {
    console.error(" ✗ Could not start tunnel:", e.message);
    appium.kill();
    process.exit(1);
  }
  console.log(` ✓ Tunnel  : ${tunnelUrl}`);

  // 5. Register with cloud backend
  console.log(` ⟳ Registering with backend...`);
  try {
    const result = await httpPost(`${backendUrl}/configure`, {
      appium_url: tunnelUrl,
      device_udid: deviceUdid,
      device_name: deviceName,
      platform_version: platformVersion,
      secret: configureSecret,
    });
    const parsed = JSON.parse(result);
    if (parsed.status === "ok") {
      console.log(` ✓ Backend : registered — device "${deviceName}" ready`);
    } else {
      console.log(` ? Backend : ${result}`);
    }
  } catch (e) {
    console.error(` ✗ Could not reach backend: ${e.message}`);
  }

  // 6. Done
  const pad = (s, n) => String(s).slice(0, n).padEnd(n);
  console.log(`
 ╔══════════════════════════════════════════════╗
 ║  All systems running!                        ║
 ║                                              ║
 ║  Backend : ${pad(backendUrl, 32)} ║
 ║  Appium  : localhost:${pad(appiumPort, 25)} ║
 ║  Tunnel  : ${pad(tunnelUrl, 32)} ║
 ║  Device  : ${pad(deviceName, 32)} ║
 ╚══════════════════════════════════════════════╝

  Open MExAgent on your phone and press ▶ Start
  Press Ctrl+C to stop everything.
`);

  process.on("SIGINT", () => {
    console.log("\n Stopping...");
    appium.kill();
    if (lt) lt.close();
    process.exit(0);
  });
})();
