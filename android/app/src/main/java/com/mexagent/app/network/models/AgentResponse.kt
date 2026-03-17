package com.mexagent.app.network.models

import com.google.gson.annotations.SerializedName

data class StartResponse(
    @SerializedName("status") val status: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("message") val message: String
)

data class StopResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String
)

data class StatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("current_screen") val currentScreen: String?,
    @SerializedName("actions_executed") val actionsExecuted: Int
)

data class LogsResponse(
    @SerializedName("logs") val logs: List<LogMessage>,
    @SerializedName("total") val total: Int,
    @SerializedName("session_id") val sessionId: String?
)

data class LogMessage(
    @SerializedName("id") val id: Long,
    @SerializedName("level") val level: String,
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("screen") val screen: String?
)

data class HighlightResponse(
    @SerializedName("active") val active: Boolean,
    @SerializedName("x") val x: Int,
    @SerializedName("y") val y: Int,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("label") val label: String,
    @SerializedName("type") val type: String,
    @SerializedName("status") val status: String,
    @SerializedName("screen") val screen: String
)
