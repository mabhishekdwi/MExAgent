package com.mexagent.app.logs

import com.mexagent.app.network.models.LogMessage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
            timestamp = convertUtcToLocal(msg.timestamp),
            screen    = msg.screen
        )

        private fun convertUtcToLocal(utcTime: String): String {
            return try {
                val utcFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                utcFmt.timeZone = TimeZone.getTimeZone("UTC")
                val date = utcFmt.parse(utcTime) ?: return utcTime
                val localFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                localFmt.format(date)
            } catch (e: Exception) {
                utcTime
            }
        }
    }
}
