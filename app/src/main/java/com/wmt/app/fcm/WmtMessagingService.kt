package com.wmt.app.fcm

import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wmt.app.di.ApplicationScope
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class WmtMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var inAppBus: InAppNotificationBus

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onNewToken(token: String) {
        // Re-register with the backend; harmlessly no-ops (401) if not logged in.
        appScope.launch { runCatching { authRepository.registerDeviceToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = PushPayload.from(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
        )

        notificationRepository.incrementUnreadLocally()
        inAppBus.emit(InAppMessage(push.title, push.body, push.target))
        showNotification(push)
    }

    private fun showNotification(push: PushPayload) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        // A message the server can identify keeps the same pending intent across
        // re-deliveries; anything else gets a fresh one.
        val requestCode = push.notificationId?.hashCode() ?: counter.incrementAndGet()
        manager.notify(counter.incrementAndGet(), buildPushNotification(this, push, requestCode))
    }

    companion object {
        private val counter = AtomicInteger(1000)
    }
}
