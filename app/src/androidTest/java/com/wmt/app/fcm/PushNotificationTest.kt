package com.wmt.app.fcm

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wmt.app.MainActivity
import com.wmt.app.domain.model.NotificationTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a push actually becomes on a device.
 *
 * The JVM tests cover which destination a payload names; these cover the two things
 * only a device can answer — that the notification the system accepts carries the
 * category this build means it to, and that the extras written onto the tap intent are
 * the extras read back off it. A push whose tap opens nothing would satisfy both sides
 * tested alone.
 */
@RunWith(AndroidJUnit4::class)
class PushNotificationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val posted = mutableListOf<Int>()

    @Before
    fun grantNotifications() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun cancelPosted() {
        posted.forEach { manager.cancel(it) }
    }

    /** Posts through the real NotificationManager and reads the notification back. */
    private fun post(push: PushPayload, id: Int): Notification {
        NotificationManagerCompat.from(context)
            .notify(id, buildPushNotification(context, push, requestCode = id))
        posted += id
        val active = manager.activeNotifications.firstOrNull { it.id == id }
        return requireNotNull(active) { "notification $id was not accepted by the system" }
            .notification
    }

    private fun push(vararg data: Pair<String, String>) = PushPayload.from(data.toMap())

    @Test
    fun anApprovalAwaitingADecisionIsLabelledAsOne() {
        val notification = post(
            push(
                "type" to "approval_requested",
                "title" to "Approval needed",
                "body" to "Petty cash reimbursement",
                "approval_project_id" to "3",
                "approval_item_id" to "88",
            ),
            id = 4001,
        )

        assertEquals("Approval needed", notification.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals("Approval needed", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Petty cash reimbursement",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(Notification.CATEGORY_EVENT, notification.category)
    }

    @Test
    fun aVerdictIsLabelledApartFromARequest() {
        val approved = post(push("type" to "approval_approved", "title" to "Approved"), id = 4002)
        assertEquals("Approved", approved.extras.getString(Notification.EXTRA_SUB_TEXT))

        val rejected = post(push("type" to "approval_rejected", "title" to "Rejected"), id = 4003)
        assertEquals("Rejected", rejected.extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun anUnknownApprovalTypeStillReadsAsApprovals() {
        val notification = post(push("type" to "approval_escalated_to_board"), id = 4004)

        assertEquals("Approvals", notification.extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun anApprovalCommentGroupsWithMessages() {
        val notification = post(push("type" to "approval_comment"), id = 4005)

        assertEquals("Approval comment", notification.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals(Notification.CATEGORY_MESSAGE, notification.category)
    }

    @Test
    fun aTaskPushIsUnchangedByTheApprovalWork() {
        val notification = post(
            push("type" to "task_assigned", "title" to "Close the books", "project_id" to "4"),
            id = 4006,
        )

        assertEquals("Task assigned", notification.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals(Notification.CATEGORY_EVENT, notification.category)
    }

    @Test
    fun theTapIntentCarriesTheApprovalRequestBackOut() {
        val push = push(
            "type" to "approval_requested",
            "approval_project_id" to "3",
            "approval_item_id" to "88",
        )

        val intent = tapIntent(context, push)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(
            NotificationTarget.ApprovalRequest(projectId = 3, requestId = 88),
            intent.notificationTarget(),
        )
    }

    @Test
    fun everyDestinationSurvivesTheIntentRoundTrip() {
        val cases = listOf(
            mapOf("type" to "task_assigned", "project_id" to "4", "task_id" to "17"),
            mapOf("type" to "task_assigned", "project_id" to "4"),
            mapOf(
                "type" to "approval_approved",
                "approval_project_id" to "3",
                "approval_item_id" to "88",
            ),
            mapOf("type" to "approval_step_activated"),
        )

        for (data in cases) {
            val push = PushPayload.from(data)
            assertEquals(
                "round trip lost $data",
                push.target,
                tapIntent(context, push).notificationTarget(),
            )
        }
    }

    @Test
    fun aLaunchIntentFromFcmItselfIsReadTheSameWay() {
        // FCM displays a notification-payload message on its own and hands the data
        // payload to the launcher as strings, which getIntExtra silently drops.
        val fromFcm = Intent(context, MainActivity::class.java)
            .putExtra("type", "approval_requested")
            .putExtra("approval_project_id", "3")
            .putExtra("approval_item_id", "88")

        assertEquals(
            NotificationTarget.ApprovalRequest(projectId = 3, requestId = 88),
            fromFcm.notificationTarget(),
        )
    }

    @Test
    fun anIntentThatNamesNothingOpensNothing() {
        assertNull(Intent(context, MainActivity::class.java).notificationTarget())
        assertTrue(posted.isEmpty())
    }
}
