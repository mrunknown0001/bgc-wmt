package com.wmt.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.wmt.app.di.MediaClient
import com.wmt.app.util.Constants
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class WmtApplication : Application(), ImageLoaderFactory {

    /**
     * Provider rather than a direct injection: Coil asks for the loader lazily, and the
     * client should not be built before it is first needed.
     */
    @Inject
    @MediaClient
    lateinit var mediaClient: Provider<OkHttpClient>

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Every image in the app loads through the authenticated client.
     *
     * Attachments and avatars are served from behind the API guard, so an unauthenticated
     * fetch comes back as a login page rather than the file. The client attaches the token
     * only for the configured server, so a URL pointing elsewhere is still fetched plainly.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { mediaClient.get() }
        .crossfade(true)
        .build()

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
