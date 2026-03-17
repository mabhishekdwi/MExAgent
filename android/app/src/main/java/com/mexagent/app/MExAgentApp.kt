package com.mexagent.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MExAgentApp : Application() {

    companion object {
        const val OVERLAY_CHANNEL_ID = "mexagent_overlay_channel"
        const val OVERLAY_NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "MExAgent Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for floating agent overlay"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
