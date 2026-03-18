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
      hostname: "localhost",
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

// ── WebSocket proxy to backend ────────────────────────────────────────────────
function connectProxy(wsUrl, appiumPort) {
  return new Promise((resolve, reject) => {
    const url    = new URL(wsUrl);
    const isSSL  = url.protocol === "wss:";
    const mod    = isSSL ? https : http;
    const port   = url.port || (isSSL ? 443 : 80);
    const key    = require("crypto").randomBytes(16).toString("base64");

    const req = mod.request({
      hostname: url.hostname,
      port,
      path: url.pathname,
      method: "GET",
      headers: {
        "Host":                   url.host,
        "Upgrade":                "websocket",
        "Connection":             "Upgrade",
        "Sec-WebSocket-Key":      key,
        "Sec-WebSocket-Version":  "13",
      },
    });

    req.on("upgrade", (_res, socket) => {
      console.log(" ✓ Proxy   : connected to backend");
      resolve(socket);

      // Parse incoming WebSocket frames and forward to Appium
      let buf = Buffer.alloc(0);
      socket.on("data", async (chunk) => {
        buf = Buffer.concat([buf, chunk]);
        while (buf.length >= 2) {
          const b0 = buf[0], b1 = buf[1];
          const masked = !!(b1 & 0x80);
          let payloadLen = b1 & 0x7f;
          let offset = 2;
          if (payloadLen === 126) { if (buf.length < 4) break; payloadLen = buf.readUInt16BE(2); offset = 4; }
          else if (payloadLen === 127) { if (buf.length < 10) break; payloadLen = Number(buf.readBigUInt64BE(2)); offset = 10; }
          const maskOffset = offset;
          if (masked) offset += 4;
          if (buf.length < offset + payloadLen) break;
          let payload = buf.subarray(offset, offset + payloadLen);
          if (masked) {
            const mask = buf.subarray(maskOffset, maskOffset + 4);
            payload = Buffer.from(payload.map((b, i) => b ^ mask[i % 4]));
          }
          buf = buf.subarray(offset + payloadLen);
          const opcode = b0 & 0x0f;
          if (opcode === 0x8) { socket.destroy(); return; } // close
          if (opcode === 0x9) { sendWsFrame(socket, Buffer.from([0x8a, 0x00])); continue; } // pong
          if (opcode === 0x1 || opcode === 0x2) {
            try {
              const msg   = JSON.parse(payload.toString());
              const appResp = await forwardToAppium(msg.method, msg.path, msg.body, appiumPort);
              const frame  = JSON.stringify({ id: msg.id, status: appResp.status, body: appResp.body });
              sendWsText(socket, frame);
            } catch (e) {
              console.error("[proxy] error:", e.message);
            }
          }
        }
      });

      socket.on("close",  () => console.log("[proxy] disconnected — restart exe to reconnect"));
      socket.on("error",  (e) => console.error("[proxy] socket error:", e.message));
    });

    req.on("error", (e) => reject(e));
    req.end();
  });
}

function sendWsText(socket, text) {
  const payload = Buffer.from(text);
  const len     = payload.length;
  let header;
  if (len < 126)       header = Buffer.from([0x81, len]);
  else if (len < 65536) header = Buffer.from([0x81, 126, len >> 8, len & 0xff]);
  else                  header = Buffer.from([0x81, 127, 0,0,0,0, len>>24,(len>>16)&0xff,(len>>8)&0xff,len&0xff]);
  socket.write(Buffer.concat([header, payload]));
}

function sendWsFrame(socket, frame) {
  socket.write(frame);
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
  const appium = spawnBackground(
    "appium",
    ["server", "--base-path", "/", "--relaxed-security", "--port", String(appiumPort)],
    "Appium"
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

  process.on("SIGINT", () => {
    console.log("\n Stopping...");
    appium.kill();
    process.exit(0);
  });
})();
