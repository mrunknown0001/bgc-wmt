package com.wmt.app.util

object Constants {
    const val DATASTORE_PREFS = "wmt_prefs"
    const val ENCRYPTED_PREFS = "wmt_secure_prefs"
    const val DEVICE_NAME = "android"
    const val PLATFORM = "android"
    const val UNREAD_POLL_INTERVAL_MS = 60_000L
    const val DEFAULT_CONNECT_TIMEOUT = 20L
    const val DEFAULT_READ_TIMEOUT = 30L
    const val DB_NAME = "wmt.db"

    // Realtime (Soketi / Pusher protocol). Host is derived from the configured server URL.
    const val WS_PORT = 6001
    const val WS_KEY = "app-key"
    // Must match the channel_id the backend FcmService sends (android.notification.channel_id).
    const val NOTIFICATION_CHANNEL_ID = "wmt_notifications"
}
