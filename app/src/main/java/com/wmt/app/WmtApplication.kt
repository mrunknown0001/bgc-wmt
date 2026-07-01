package com.wmt.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wmt.app.util.Constants
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WmtApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.default_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Task and project updates from WMT"
        }
        manager.createNotificationChannel(channel)
    }
}
