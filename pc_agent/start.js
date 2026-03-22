#!/usr/bin/env node
/**
 * MExAgent PC Agent — single command setup
 * npm start  OR  double-click mexagent-pc.exe
 *
 * No tunnel needed. PC opens a WebSocket to the backend.
 * Backend forwards Appium HTTP requests through the WebSocket to this agent.
 * Agent calls local Appium and returns responses. Pure outbound connections.
 */

const { execSync, spawn } = require("child_process");
const http  = require("http");
const https = require("https");
const fs    = require("fs");
const path  = require("path");
const WebSocket = require("ws");

// ── Load config ───────────────────────────────────────────────────────────────
const exeDir = process.pkg ? path.dirname(process.execPath) : __dirname;
const configPath = path.join(exeDir, "config.json");
if (!fs.existsSync(configPath)) {
  console.error("❌  config.json not found next to mexagent-pc.exe.");
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

function spawnBackground(cmd, args, label, extraEnv = {}) {
  const proc = spawn(cmd, args, {
    stdio: ["ignore", "pipe", "pipe"],
    shell: process.platform === "win32",
    detached: false,
    env: { ...process.env, ...extraEnv },
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
    const parsed  = new URL(url);
    const mod      = parsed.protocol === "https:" ? https : http;
    const req = mod.request({
      hostname: parsed.hostname,
      port:     parsed.port || (parsed.protocol === "https:" ? 443 : 80),
      path:     parsed.pathname,
      method:   "POST",
      headers: {
        "Content-Type":   "application/json",
        "Content-Length": Buffer.byteLength(payload),
      },
    }, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => resolve(data));
    });
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
  console.log(" ⟳ Appium not found — installing (npm install -g appium)...");
  try {
    run("npm install -g appium", { stdio: "inherit" });
    console.log(" ✓ Appium  : installed");
  } catch (e) {
    console.error(" ✗ Failed to install Appium:", e.message);
    process.exit(1);
  }
}

function ensureUiautomator2() {
  let list = "";
  try {
    const result = require("child_process").spawnSync(
      "appium", ["driver", "list", "--installed"],
      { encoding: "utf8", shell: process.platform === "win32" }
    );
    list = (result.stdout || "") + (result.stderr || "");
  } catch {}

  if (list.includes("uiautomator2")) {
    console.log(" ✓ Driver  : uiautomator2 already installed");
    return;
  }
  console.log(" ⟳ Installing uiautomator2 driver...");
  try {
    const result = require("child_process").spawnSync(
      "appium", ["driver", "install", "uiautomator2"],
      { encoding: "utf8", shell: process.platform === "win32" }
    );
    const out = (result.stdout || "") + (result.stderr || "");
    if (result.status !== 0 && !out.includes("already installed")) {
      throw new Error(out);
    }
    console.log(" ✓ Driver  : uiautomator2 installed");
  } catch (e) {
    console.error(" ✗ Failed to install uiautomator2:", e.message);
    process.exit(1);
  }
}

// ── Forward one HTTP request to local Appium ──────────────────────────────────
function forwardToAppium(method, reqPath, body, port) {
  return new Promise((resolve) => {
    const options = {
      hostname: "127.0.0.1",
      port,
      path: reqPath,
      method,
      headers: { "Content-Type": "application/json" },
    };
    const req = http.request(options, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => resolve({ status: res.statusCode, body: data }));
    });
    req.on("error", (e) => resolve({ status: 500, body: JSON.stringify({ error: e.message }) }));
    if (body) req.write(body);
    req.end();
  });
}

// ── WebSocket proxy to backend (using ws library — handles masking/framing correctly) ──
async function connectProxy(wsUrl, appiumPort) {
  return new Promise((resolve) => {
    let resolved = false;

    const tryConnect = () => {
      const ws = new WebSocket(wsUrl, {
        handshakeTimeout: 10000,
        perMessageDeflate: false,
      });

      // Keepalive ping every 20s so Render doesn't drop idle connections
      const pingInterval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.ping();
      }, 20000);

      ws.on("open", () => {
        if (!resolved) {
          resolved = true;
          console.log(" ✓ Proxy   : connected to backend");
          resolve();
        } else {
          console.log("[proxy] reconnected to backend");
        }
      });

      ws.on("message", async (data) => {
        try {
          const msg     = JSON.parse(data.toString());
          const appResp = await forwardToAppium(msg.method, msg.path, msg.body, appiumPort);
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ id: msg.id, status: appResp.status, body: appResp.body }));
          }
        } catch (e) {
          console.error("[proxy] error:", e.message);
        }
      });

      ws.on("close", () => {
        clearInterval(pingInterval);
        console.log("[proxy] connection dropped — reconnecting in 5s...");
        setTimeout(tryConnect, 5000);
      });

      ws.on("error", (e) => {
        clearInterval(pingInterval);
        if (!resolved) {
          resolved = true;
          console.error(" ✗ Proxy connection failed:", e.message);
          resolve();
        }
      });
    };

    tryConnect();

    setTimeout(() => {
      if (!resolved) {
        resolved = true;
        console.error(" ✗ Proxy connection timed out");
        resolve();
      }
    }, 15000);
  });
}

// ── Main ──────────────────────────────────────────────────────────────────────
(async () => {
  console.log("\n MExAgent PC Agent\n ==================\n");

  // 1. Ensure Appium + driver
  ensureAppium();
  ensureUiautomator2();

  // 2. Detect device
  let deviceUdid = "", deviceName = "", platformVersion = "";
  try {
    const devices = run("adb devices");
    const match   = devices.split("\n").find((l) => l.includes("\tdevice"));
    if (!match) throw new Error("no device");
    deviceUdid       = match.split("\t")[0].trim();
    deviceName       = run("adb shell getprop ro.product.model").replace(/\r/g, "");
    platformVersion  = run("adb shell getprop ro.build.version.release").replace(/\r/g, "");
    console.log(` ✓ Device  : ${deviceName} | Android ${platformVersion} | ${deviceUdid}`);
  } catch {
    console.error(" ✗ No Android device found via ADB.");
    console.error("   Connect phone via USB and enable USB Debugging.");
    process.exit(1);
  }

  // 3. Start Appium
  console.log(` ⟳ Starting Appium on port ${appiumPort}...`);

  // Detect Android SDK location
  const possibleSdkPaths = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    path.join(process.env.LOCALAPPDATA || "", "Android", "Sdk"),
    path.join(process.env.USERPROFILE || "", "AppData", "Local", "Android", "Sdk"),
  ].filter(Boolean);
  const androidHome = possibleSdkPaths.find((p) => fs.existsSync(p)) || "";
  if (androidHome) console.log(` ✓ Android SDK: ${androidHome}`);

  const appium = spawnBackground(
    "appium",
    ["server", "--base-path", "/", "--relaxed-security", "--port", String(appiumPort)],
    "Appium",
    androidHome ? { ANDROID_HOME: androidHome, ANDROID_SDK_ROOT: androidHome } : {}
  );
  await sleep(3000);
  console.log(` ✓ Appium  : running on port ${appiumPort}`);

  // 4. Register device info with backend
  console.log(` ⟳ Registering device with backend...`);
  try {
    const result = await httpPost(`${backendUrl}/configure`, {
      appium_url:       `http://localhost:${appiumPort}`,
      device_udid:      deviceUdid,
      device_name:      deviceName,
      platform_version: platformVersion,
      secret:           configureSecret,
    });
    const parsed = JSON.parse(result);
    if (parsed.status === "ok") {
      console.log(` ✓ Backend : device "${deviceName}" registered`);
    }
  } catch (e) {
    console.error(` ✗ Could not reach backend: ${e.message}`);
  }

  // 5. Connect WebSocket proxy (no tunnel needed)
  console.log(` ⟳ Connecting proxy to backend...`);
  const wsUrl = backendUrl.replace(/^https/, "wss").replace(/^http/, "ws") + "/ws/appium";
  try {
    await connectProxy(wsUrl, appiumPort);
  } catch (e) {
    console.error(" ✗ Could not connect proxy:", e.message);
    appium.kill();
    process.exit(1);
  }

  // 6. Done
  const pad = (s, n) => String(s).slice(0, n).padEnd(n);
  console.log(`
 ╔══════════════════════════════════════════════╗
 ║  All systems running!                        ║
 ║                                              ║
 ║  Backend : ${pad(backendUrl, 32)} ║
 ║  Appium  : localhost:${pad(appiumPort, 25)} ║
 ║  Device  : ${pad(deviceName, 32)} ║
 ╚══════════════════════════════════════════════╝

  Open MExAgent on your phone and press ▶ Start
  Press Ctrl+C to stop everything.
`);

  // 7. Save logs to local file
  const logFile = path.join(exeDir, "mexagent-logs.txt");
  let lastLogId = 0;
  fs.writeFileSync(logFile, `MExAgent Log — started ${new Date().toLocaleString()}\n${"=".repeat(60)}\n`);
  console.log(` ✓ Logs    : saving to ${logFile}`);

  setInterval(async () => {
    try {
      const url = `${backendUrl}/logs${lastLogId > 0 ? `?since_id=${lastLogId}` : ""}`;
      const parsed = new URL(url);
      const mod = parsed.protocol === "https:" ? https : http;
      mod.get({ hostname: parsed.hostname, port: parsed.port || 443, path: parsed.pathname + parsed.search,
        headers: { "Accept": "application/json" } }, (res) => {
        let data = "";
        res.on("data", (c) => data += c);
        res.on("end", () => {
          try {
            const body = JSON.parse(data);
            const entries = body.logs || [];
            if (entries.length > 0) {
              const lines = entries.map(e => `[${e.timestamp}] [${e.level}] ${e.message}`).join("\n");
              fs.appendFileSync(logFile, lines + "\n");
              lastLogId = Math.max(...entries.map(e => e.id));
            }
          } catch {}
        });
      }).on("error", () => {});
    } catch {}
  }, 3000);

  process.on("SIGINT", () => {
    console.log("\n Stopping...");
    appium.kill();
    process.exit(0);
  });
})();
