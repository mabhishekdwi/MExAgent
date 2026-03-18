#!/usr/bin/env node
/**
 * MExAgent PC Agent
 * Single command: node start.js
 * Starts Appium + ngrok, registers with cloud backend automatically.
 */

const { execSync, spawn } = require("child_process");
const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");

// ── Load config ───────────────────────────────────────────────────────────────
const configPath = path.join(__dirname, "config.json");
if (!fs.existsSync(configPath)) {
  console.error("❌  config.json not found. Copy config.json and set your backendUrl.");
  process.exit(1);
}
const config = JSON.parse(fs.readFileSync(configPath, "utf8"));
const { backendUrl, configureSecret, appiumPort = 4723 } = config;

if (!backendUrl || backendUrl.includes("your-app")) {
  console.error("❌  Edit config.json and set your Render backend URL first.");
  process.exit(1);
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function run(cmd) {
  return execSync(cmd, { encoding: "utf8" }).trim();
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
    if (code !== 0 && code !== null) console.log(`[${label}] exited with code ${code}`);
  });
  return proc;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function httpGet(url) {
  return new Promise((resolve, reject) => {
    const mod = url.startsWith("https") ? https : http;
    mod.get(url, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => resolve(data));
    }).on("error", reject);
  });
}

function httpPost(url, body) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(body);
    const parsed = new URL(url);
    const mod = parsed.protocol === "https:" ? https : http;
    const req = mod.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "https:" ? 443 : 80),
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

// ── Main ──────────────────────────────────────────────────────────────────────
(async () => {
  console.log("\n MExAgent PC Agent\n ==================\n");

  // 1. Detect device
  let deviceUdid = "", deviceName = "", platformVersion = "";
  try {
    const devices = run("adb devices");
    const match = devices.split("\n").find((l) => l.includes("\tdevice"));
    if (!match) throw new Error("no device");
    deviceUdid = match.split("\t")[0].trim();
    deviceName = run("adb shell getprop ro.product.model").replace(/\r/g, "");
    platformVersion = run("adb shell getprop ro.build.version.release").replace(/\r/g, "");
    console.log(` ✓ Device : ${deviceName} | Android ${platformVersion} | ${deviceUdid}`);
  } catch {
    console.error(" ✗ No Android device found via ADB.");
    console.error("   Connect phone via USB and enable USB Debugging.");
    process.exit(1);
  }

  // 2. Start Appium
  console.log(` ⟳ Starting Appium on port ${appiumPort}...`);
  const appium = spawnBackground(
    "appium",
    ["server", "--base-path", "/", "--relaxed-security", "--port", String(appiumPort)],
    "Appium"
  );
  await sleep(3000);
  console.log(` ✓ Appium  : running on port ${appiumPort}`);

  // 3. Start ngrok
  console.log(` ⟳ Starting ngrok tunnel...`);
  const ngrok = spawnBackground("ngrok", ["http", String(appiumPort)], "ngrok");
  await sleep(4000);

  // 4. Get ngrok URL from its local API
  let ngrokUrl = "";
  for (let i = 0; i < 5; i++) {
    try {
      const resp = await httpGet("http://localhost:4040/api/tunnels");
      const data = JSON.parse(resp);
      ngrokUrl = data.tunnels?.[0]?.public_url || "";
      if (ngrokUrl) break;
    } catch {}
    await sleep(1000);
  }
  if (!ngrokUrl) {
    console.error(" ✗ Could not get ngrok URL. Is ngrok installed and authenticated?");
    appium.kill(); ngrok.kill();
    process.exit(1);
  }
  console.log(` ✓ ngrok   : ${ngrokUrl}`);

  // 5. Register with cloud backend
  console.log(` ⟳ Registering with backend: ${backendUrl}`);
  try {
    const result = await httpPost(`${backendUrl}/configure`, {
      appium_url: ngrokUrl,
      device_udid: deviceUdid,
      device_name: deviceName,
      platform_version: platformVersion,
      secret: configureSecret,
    });
    const parsed = JSON.parse(result);
    if (parsed.status === "ok") {
      console.log(` ✓ Backend : registered successfully`);
    } else {
      console.log(` ✓ Backend : ${result}`);
    }
  } catch (e) {
    console.error(` ✗ Could not reach backend: ${e.message}`);
    console.error(`   Make sure ${backendUrl} is deployed and running.`);
  }

  // 6. Done
  console.log(`
 ╔══════════════════════════════════════════════╗
 ║  All systems running!                        ║
 ║                                              ║
 ║  Backend : ${backendUrl.padEnd(32)} ║
 ║  Appium  : localhost:${String(appiumPort).padEnd(25)} ║
 ║  Tunnel  : ${ngrokUrl.padEnd(32)} ║
 ║  Device  : ${deviceName.padEnd(32)} ║
 ╚══════════════════════════════════════════════╝

  Open MExAgent on your phone and press ▶ Start
  Press Ctrl+C to stop everything.
`);

  // Keep alive — kill children on exit
  process.on("SIGINT", () => {
    console.log("\n Stopping...");
    appium.kill();
    ngrok.kill();
    process.exit(0);
  });
})();
