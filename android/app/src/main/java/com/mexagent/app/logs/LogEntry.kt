package com.mexagent.app.logs

import com.mexagent.app.network.models.LogMessage

data class LogEntry(
    val id: Long,
    val level: String,
    val message: String,
    val timestamp: String,
    val screen: String?
) {
    companion object {
        fun fromNetwork(msg: LogMessage) = LogEntry(
            id        = msg.id,
            level     = msg.level,
            message   = msg.message,
            timestamp = msg.timestamp,
            screen    = msg.screen
        )
    }
}
