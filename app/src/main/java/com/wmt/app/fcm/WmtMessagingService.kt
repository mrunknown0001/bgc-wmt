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
import com.wmt.app.domain.model.NotificationTarget
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // The extras carry the server's own key names, so a tap reads the same
            // whether this notification posted it or FCM launched the activity itself
            // from a notification-payload message, which passes the data through as
            // strings.
            push.type?.let { putExtra(MainActivity.EXTRA_TYPE, it) }
            when (val target = push.target) {
                is NotificationTarget.Task -> {
                    putExtra(MainActivity.EXTRA_PROJECT_ID, target.projectId)
                    putExtra(MainActivity.EXTRA_TASK_ID, target.taskId)
                }
                is NotificationTarget.Project ->
                    putExtra(MainActivity.EXTRA_PROJECT_ID, target.projectId)
                is NotificationTarget.ApprovalRequest -> {
                    putExtra(MainActivity.EXTRA_APPROVAL_PROJECT_ID, target.projectId)
                    putExtra(MainActivity.EXTRA_APPROVAL_REQUEST_ID, target.requestId)
                }
                // The type alone already lands on the queue; nothing more to carry.
                NotificationTarget.ApprovalQueue, null -> Unit
            }
            push.notificationId?.let { putExtra(MainActivity.EXTRA_NOTIFICATION_ID, it) }
        }
        val requestCode = push.notificationId?.hashCode() ?: counter.incrementAndGet()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val category = categoryLabel(push.type)

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(push.title)
            .setContentText(push.body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(push.title)
                    .bigText(push.body)
                    .setSummaryText(category),
            )
            .setSubText(category)
            .setColor(BRAND_COLOR)
            .setCategory(systemCategory(push.type))
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

    /**
     * Human-readable category shown as the notification sub-text.
     *
     * Approvals carry two quite different messages — a request waiting on you, and the
     * verdict on one you raised — so they are labelled apart rather than sharing a
     * single line. An approval type this build has not seen still says "Approvals"
     * rather than falling through to "Workload".
     */
    private fun categoryLabel(type: String?): String = when (type) {
        "task_assigned" -> "Task assigned"
        "task_due_soon", "task_due_reminder", "task_overdue" -> "Due date"
        "task_comment", "subtask_comment" -> "Comment"
        "task_comment_mention" -> "Mention"
        "comment_deleted" -> "Comment"
        "task_escalated" -> "Escalation"
        "approval_requested", "approval_pending", "approval_step_activated" -> "Approval needed"
        "approval_approved" -> "Approved"
        "approval_rejected" -> "Rejected"
        "approval_changes_requested" -> "Changes requested"
        "approval_resubmitted" -> "Resubmitted"
        "approval_comment" -> "Approval comment"
        else -> if (NotificationTarget.isApprovalType(type)) "Approvals" else "Workload"
    }

    /** Maps to a system notification category for ranking/grouping. */
    private fun systemCategory(type: String?): String = when (type) {
        "task_comment", "subtask_comment", "task_comment_mention", "comment_deleted",
        "approval_comment",
        -> NotificationCompat.CATEGORY_MESSAGE
        "task_due_soon", "task_due_reminder", "task_overdue" ->
            NotificationCompat.CATEGORY_REMINDER
        else -> NotificationCompat.CATEGORY_EVENT
    }

    companion object {
        private val counter = AtomicInteger(1000)
        private const val BRAND_COLOR = 0xFF2D6CDF.toInt()
    }
}
