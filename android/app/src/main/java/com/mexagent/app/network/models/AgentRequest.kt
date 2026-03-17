package com.mexagent.app.network.models

import com.google.gson.annotations.SerializedName

data class StartRequest(
    @SerializedName("depth") val depth: Int = 2,
    @SerializedName("ai_mode") val aiMode: Boolean = true,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("action_delay_ms") val actionDelayMs: Int? = null
)

data class StopRequest(
    @SerializedName("session_id") val sessionId: String? = null
)

data class ActionRequest(
    @SerializedName("action") val action: String,
    @SerializedName("target") val target: String,
    @SerializedName("value") val value: String? = null
)
