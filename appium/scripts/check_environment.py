#!/usr/bin/env python3
"""
Pre-flight environment checker for MExAgent.
Run before starting the backend: python check_environment.py
"""
import subprocess
import sys
import shutil
import json


def ok(msg):   print(f"  \033[92m[OK]\033[0m  {msg}")
def warn(msg): print(f"  \033[93m[WARN]\033[0m {msg}")
def fail(msg): print(f"  \033[91m[FAIL]\033[0m {msg}")


def check_command(cmd: str, version_flag: str = "--version") -> bool:
    if shutil.which(cmd) is None:
        fail(f"{cmd} not found in PATH")
        return False
    try:
        out = subprocess.check_output([cmd, version_flag], stderr=subprocess.STDOUT).decode()
        ok(f"{cmd}: {out.strip().splitlines()[0]}")
        return True
    except Exception as e:
        warn(f"{cmd} found but version check failed: {e}")
        return True


def check_java():
    print("\n[1] Java")
    return check_command("java", "-version")


def check_adb():
    print("\n[2] ADB / Android SDK")
    if not check_command("adb", "version"):
        return False
    # List devices
    try:
        out = subprocess.check_output(["adb", "devices"], stderr=subprocess.STDOUT).decode()
        lines = [l for l in out.splitlines() if "\t" in l]
        if lines:
            ok(f"Connected devices: {len(lines)}")
            for l in lines:
                ok(f"  {l}")
        else:
            warn("No Android devices / emulators connected")
    except Exception:
        warn("Could not list ADB devices")
    return True


def check_node():
    print("\n[3] Node.js / npm")
    return check_command("node", "--version") and check_command("npm", "--version")


def check_appium():
    print("\n[4] Appium")
    if not check_command("appium", "--version"):
        fail("Install with: npm install -g appium")
        return False
    # Check UiAutomator2 driver
    try:
        out = subprocess.check_output(
            ["appium", "driver", "list", "--installed", "--json"],
            stderr=subprocess.STDOUT,
        ).decode()
        drivers = json.loads(out)
        if any("uiautomator2" in str(k).lower() for k in drivers.keys()):
            ok("UiAutomator2 driver installed")
        else:
            warn("UiAutomator2 driver missing — run: appium driver install uiautomator2")
    except Exception:
        warn("Could not query Appium drivers")
    return True


def check_python_deps():
    print("\n[5] Python dependencies")
    try:
        import fastapi;           ok(f"fastapi {fastapi.__version__}")
    except ImportError:           fail("fastapi not installed")
    try:
        import appium;            ok("appium-python-client installed")
    except ImportError:           fail("Appium-Python-Client not installed")
    try:
        import groq;              ok("groq installed")
    except ImportError:           warn("groq not installed (needed for Groq LLM provider)")
    try:
        import httpx;             ok(f"httpx {httpx.__version__}")
    except ImportError:           fail("httpx not installed")


def check_appium_server():
    print("\n[6] Appium server reachability")
    try:
        import urllib.request
        with urllib.request.urlopen("http://localhost:4723/status", timeout=3) as r:
            data = json.loads(r.read())
            if data.get("value", {}).get("ready"):
                ok("Appium server is running at http://localhost:4723")
            else:
                warn("Appium server responded but 'ready' is false")
    except Exception:
        warn("Appium server not reachable — start with: appium server --port 4723")


if __name__ == "__main__":
    print("=" * 55)
    print("  MExAgent — Environment Pre-flight Check")
    print("=" * 55)
    check_java()
    check_adb()
    check_node()
    check_appium()
    check_python_deps()
    check_appium_server()
    print("\n" + "=" * 55)
    print("Done. Fix any [FAIL] items before starting the backend.")
    print("=" * 55)
