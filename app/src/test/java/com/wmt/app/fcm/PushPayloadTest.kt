package com.wmt.app.fcm

import com.wmt.app.domain.model.NotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a push points.
 *
 * FCM data is untyped strings, and the same map is read twice — once when the message
 * arrives, once when the tap comes back through the launch intent — so a key this
 * parser misses is a notification that silently opens nothing.
 */
class PushPayloadTest {

    @Test
    fun `a task push opens the task`() {
        val target = PushPayload.targetOf(
            mapOf("type" to "task_assigned", "project_id" to "4", "task_id" to "17"),
        )

        assertEquals(NotificationTarget.Task(projectId = 4, taskId = 17), target)
    }

    @Test
    fun `a task without its project falls back to the project, then to nothing`() {
        // The task route is keyed by both ids, so a bare task id cannot open it.
        assertNull(PushPayload.targetOf(mapOf("type" to "task_assigned", "task_id" to "17")))
        assertEquals(
            NotificationTarget.Project(projectId = 4),
            PushPayload.targetOf(mapOf("type" to "task_assigned", "project_id" to "4")),
        )
    }

    @Test
    fun `an approval push opens the request`() {
        val target = PushPayload.targetOf(
            mapOf(
                "type" to "approval_requested",
                "approval_project_id" to "3",
                "approval_item_id" to "88",
            ),
        )

        assertEquals(NotificationTarget.ApprovalRequest(projectId = 3, requestId = 88), target)
    }

    @Test
    fun `approval_request_id is accepted as the item id`() {
        val target = PushPayload.targetOf(
            mapOf(
                "type" to "approval_approved",
                "approval_project_id" to "3",
                "approval_request_id" to "88",
            ),
        )

        assertEquals(NotificationTarget.ApprovalRequest(projectId = 3, requestId = 88), target)
    }

    @Test
    fun `an approval push naming no request lands on the queue`() {
        // The detail route needs the approval project too; without it the queue is the
        // nearest place that still shows what arrived.
        assertEquals(
            NotificationTarget.ApprovalQueue,
            PushPayload.targetOf(mapOf("type" to "approval_step_activated")),
        )
        assertEquals(
            NotificationTarget.ApprovalQueue,
            PushPayload.targetOf(mapOf("type" to "approval_rejected", "approval_item_id" to "88")),
        )
    }

    @Test
    fun `an approval push wins over a task it also names`() {
        val target = PushPayload.targetOf(
            mapOf(
                "type" to "approval_comment",
                "project_id" to "4",
                "task_id" to "17",
                "approval_project_id" to "3",
                "approval_item_id" to "88",
            ),
        )

        assertEquals(NotificationTarget.ApprovalRequest(projectId = 3, requestId = 88), target)
    }

    @Test
    fun `unqualified ids are read only on an approval message`() {
        assertEquals(
            NotificationTarget.ApprovalRequest(projectId = 3, requestId = 88),
            PushPayload.targetOf(
                mapOf(
                    "type" to "approval_requested",
                    "approval_project_id" to "3",
                    "item_id" to "88",
                ),
            ),
        )
        // On a task message nothing claims `item_id`, so it must not become a request.
        assertEquals(
            NotificationTarget.Task(projectId = 4, taskId = 17),
            PushPayload.targetOf(
                mapOf(
                    "type" to "task_comment",
                    "project_id" to "4",
                    "task_id" to "17",
                    "item_id" to "88",
                ),
            ),
        )
    }

    @Test
    fun `blank, zero and unparseable ids count as absent`() {
        assertNull(
            PushPayload.targetOf(
                mapOf("type" to "task_assigned", "project_id" to "0", "task_id" to ""),
            ),
        )
        assertNull(PushPayload.targetOf(mapOf("type" to "task_assigned", "project_id" to "null")))
    }

    @Test
    fun `a message with nothing to open has no target`() {
        assertNull(PushPayload.targetOf(mapOf("type" to "task_escalated")))
        assertNull(PushPayload.targetOf(emptyMap()))
    }

    @Test
    fun `title and body come from the data, then the notification half, then a default`() {
        val fromData = PushPayload.from(
            data = mapOf("title" to "Approval needed", "body" to "Petty cash"),
            notificationTitle = "ignored",
            notificationBody = "ignored",
        )
        assertEquals("Approval needed", fromData.title)
        assertEquals("Petty cash", fromData.body)

        val fromNotification = PushPayload.from(
            data = emptyMap(),
            notificationTitle = "Approval needed",
            notificationBody = "Petty cash",
        )
        assertEquals("Approval needed", fromNotification.title)
        assertEquals("Petty cash", fromNotification.body)

        val bare = PushPayload.from(emptyMap())
        assertEquals("WMT", bare.title)
        assertEquals("", bare.body)
    }

    @Test
    fun `the notification id is carried through for the pending intent`() {
        val push = PushPayload.from(
            mapOf("type" to "approval_requested", "notification_id" to "9f3c-11"),
        )

        assertEquals("9f3c-11", push.notificationId)
        assertEquals("approval_requested", push.type)
    }

    @Test
    fun `every key the router reads can be carried on an intent`() {
        // MainActivity rebuilds the data map from intent extras using TARGET_KEYS; a key
        // read here but missing there is a tap that loses its destination.
        val routed = mapOf(
            "type" to "approval_requested",
            "project_id" to "4",
            "task_id" to "17",
            "approval_project_id" to "3",
            "approval_item_id" to "88",
            "approval_request_id" to "88",
            "item_id" to "88",
            "request_id" to "88",
        )

        assertEquals(routed.keys, PushPayload.TARGET_KEYS.toSet())
    }
}
