package com.mexagent.app.utils

object Constants {
    const val PREFS_NAME = "mexagent_prefs"

    const val KEY_BACKEND_URL = "backend_url"
    const val KEY_DEPTH = "depth"
    const val KEY_AI_MODE = "ai_mode"

    const val DEFAULT_BACKEND_URL = "https://mexagent.onrender.com"
    const val DEFAULT_DEPTH = 2
    const val DEFAULT_AI_MODE = true

    const val LOG_POLL_INTERVAL_MS = 2000L

    // Connection check
    const val KEY_APPIUM_URL = "appium_url"
    const val DEFAULT_APPIUM_URL = "http://192.168.1.10:4723"
}
