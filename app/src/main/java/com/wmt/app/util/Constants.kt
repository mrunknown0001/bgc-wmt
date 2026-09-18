package com.wmt.app.util

object Constants {
    const val DATASTORE_PREFS = "wmt_prefs"
    const val ENCRYPTED_PREFS = "wmt_secure_prefs"
    const val DEVICE_NAME = "android"
    const val PLATFORM = "android"
    const val UNREAD_POLL_INTERVAL_MS = 60_000L

    // Token rotation. Sanctum issues 180-day tokens but revokes any that go 60 days
    // unused, so the swap can only happen while the app is actually running: check
    // periodically, and rotate once the remaining life drops under the margin.
    const val TOKEN_REFRESH_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    const val TOKEN_REFRESH_MARGIN_MS = 30L * 24 * 60 * 60 * 1000
    const val DEFAULT_CONNECT_TIMEOUT = 20L
    const val DEFAULT_READ_TIMEOUT = 30L
    const val DEFAULT_WRITE_TIMEOUT = 120L
    const val DB_NAME = "wmt.db"

    // Host only — no /api suffix: Retrofit paths already carry `api/`, and the setup
    // screen probes `$serverUrl/api/health`. Debug builds prefill staging.
    const val STAGING_BASE_URL = "https://wmt-dev.bfcgroup.ph"

    // Realtime (Soketi / Pusher protocol). Host is derived from the configured server URL.
    const val WS_PORT = 6001
    const val WS_KEY = "app-key"
    // Must match the channel_id the backend FcmService sends (android.notification.channel_id).
    const val NOTIFICATION_CHANNEL_ID = "wmt_notifications"
}
