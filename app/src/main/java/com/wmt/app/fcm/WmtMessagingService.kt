package com.wmt.app.fcm

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wmt.app.MainActivity
import com.wmt.app.R
import com.wmt.app.di.ApplicationScope
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.NotificationRepository
import com.wmt.app.util.Constants
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
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "WMT"
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val type = data["type"]
        val projectId = data["project_id"]?.toIntOrNull()
        val taskId = data["task_id"]?.toIntOrNull()

        notificationRepository.incrementUnreadLocally()
        inAppBus.emit(InAppMessage(title, body, projectId, taskId))
        showNotification(title, body, type, projectId, taskId, data["notification_id"])
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String?,
        projectId: Int?,
        taskId: Int?,
        notificationId: String?,
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            projectId?.let { putExtra(MainActivity.EXTRA_PROJECT_ID, it) }
            taskId?.let { putExtra(MainActivity.EXTRA_TASK_ID, it) }
            notificationId?.let { putExtra(MainActivity.EXTRA_NOTIFICATION_ID, it) }
        }
        val requestCode = notificationId?.hashCode() ?: counter.incrementAndGet()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val category = categoryLabel(type)

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(body)
                    .setSummaryText(category),
            )
            .setSubText(category)
            .setColor(BRAND_COLOR)
            .setCategory(systemCategory(type))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            manager.notify(counter.incrementAndGet(), notification)
        }
    }

    /** Human-readable category shown as the notification sub-text. */
    private fun categoryLabel(type: String?): String = when (type) {
        "task_assigned" -> "Task assigned"
        "task_due_soon", "task_due_reminder", "task_overdue" -> "Due date"
        "task_comment", "subtask_comment" -> "Comment"
        "task_comment_mention" -> "Mention"
        "comment_deleted" -> "Comment"
        "task_escalated" -> "Escalation"
        else -> "Workload"
    }

    /** Maps to a system notification category for ranking/grouping. */
    private fun systemCategory(type: String?): String = when (type) {
        "task_comment", "subtask_comment", "task_comment_mention", "comment_deleted" ->
            NotificationCompat.CATEGORY_MESSAGE
        "task_due_soon", "task_due_reminder", "task_overdue" ->
            NotificationCompat.CATEGORY_REMINDER
        else -> NotificationCompat.CATEGORY_EVENT
    }

    companion object {
        private val counter = AtomicInteger(1000)
        private const val BRAND_COLOR = 0xFF2D6CDF.toInt()
    }
}
