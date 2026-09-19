package com.wmt.app.fcm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wmt.app.MainActivity
import com.wmt.app.R
import com.wmt.app.domain.model.NotificationTarget
import com.wmt.app.util.Constants

/**
 * What a push looks like once it reaches the status bar, and where tapping it goes.
 *
 * The writer and the reader of the tap intent live together on purpose: the extras one
 * puts on are the extras the other takes off, and a notification whose tap opens
 * nothing is the kind of break no test on either side alone would catch.
 */

private const val BRAND_COLOR = 0xFF2D6CDF.toInt()

private const val MISSING_ID = -1

/**
 * The launch intent a tap fires.
 *
 * The extras carry the server's own key names, so a tap reads the same whether this app
 * posted the notification or FCM displayed a notification-payload message itself and
 * handed the data straight to the launcher as string extras.
 */
internal fun tapIntent(context: Context, push: PushPayload): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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

/** Reads those extras — or FCM's own — back into the destination they describe. */
internal fun Intent.notificationTarget(): NotificationTarget? {
    val data = PushPayload.TARGET_KEYS.mapNotNull { key ->
        extraAsString(key)?.let { key to it }
    }.toMap()
    return PushPayload.targetOf(data)
}

/** Extras arrive as ints from this app and as strings from FCM; accept either. */
private fun Intent.extraAsString(key: String): String? =
    getStringExtra(key) ?: getIntExtra(key, MISSING_ID).takeIf { it != MISSING_ID }?.toString()

internal fun buildPushNotification(
    context: Context,
    push: PushPayload,
    requestCode: Int,
): Notification {
    val pendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        tapIntent(context, push),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val category = categoryLabel(push.type)

    return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
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
}

/**
 * Human-readable category shown as the notification sub-text.
 *
 * Approvals carry two quite different messages — a request waiting on you, and the
 * verdict on one you raised — so they are labelled apart rather than sharing a single
 * line. An approval type this build has not seen still says "Approvals" rather than
 * falling through to "Workload".
 */
internal fun categoryLabel(type: String?): String = when (type) {
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
internal fun systemCategory(type: String?): String = when (type) {
    "task_comment", "subtask_comment", "task_comment_mention", "comment_deleted",
    "approval_comment",
    -> NotificationCompat.CATEGORY_MESSAGE
    "task_due_soon", "task_due_reminder", "task_overdue" ->
        NotificationCompat.CATEGORY_REMINDER
    else -> NotificationCompat.CATEGORY_EVENT
}
