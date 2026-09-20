package com.wmt.app.data.remote.dto

import com.squareup.moshi.Moshi
import com.wmt.app.domain.model.NotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing for the stored notifications GET /api/notifications lists.
 *
 * The inbox routes a tap from these the same way a push is routed, so the approval ids
 * have to survive the mapping or the list opens nothing while the banner works.
 */
class NotificationMapperTest {

    private val moshi = Moshi.Builder().build()

    private fun parse(json: String) =
        requireNotNull(moshi.adapter(NotificationDto::class.java).fromJson(json)).toDomain()

    @Test
    fun `an approval notification opens its request`() {
        val notification = parse(
            """
            {
              "id": "9f3c-11",
              "data": {
                "title": "Approval needed",
                "type": "approval_requested",
                "approval_project_id": 3,
                "approval_item_id": 88
              },
              "read_at": null,
              "created_at": "2026-09-19T08:00:00+08:00"
            }
            """.trimIndent(),
        )

        assertEquals(
            NotificationTarget.ApprovalRequest(projectId = 3, requestId = 88),
            notification.data.target,
        )
    }

    @Test
    fun `an approval notification without its project still reaches the queue`() {
        val notification = parse(
            """
            {
              "id": "9f3c-12",
              "data": {"title": "Approved", "type": "approval_approved"},
              "created_at": "2026-09-19T08:00:00+08:00"
            }
            """.trimIndent(),
        )

        assertEquals(NotificationTarget.ApprovalQueue, notification.data.target)
    }

    // The body here carries no markup, so it never reaches android.text.Html.
    @Test
    fun `an approval request notification reads as one`() {
        val notification = parse(
            """
            {
              "id": "9f3c-21",
              "data": {
                "type": "approval_requested",
                "approval_project_id": 1,
                "approval_item_id": 2,
                "approval_project_name": "sample approval",
                "item_title": "Petty cash reimbursement",
                "requester": "Dana Cruz",
                "step_name": "step 1"
              },
              "created_at": "2026-09-19T08:00:00+08:00"
            }
            """.trimIndent(),
        )

        assertEquals("Dana Cruz raised a request", notification.data.title)
        assertEquals("Petty cash reimbursement", notification.data.body)
        assertEquals(
            NotificationTarget.ApprovalRequest(projectId = 1, requestId = 2),
            notification.data.target,
        )
    }

    @Test
    fun `a decision names who made it and what was decided`() {
        val approved = parse(
            """
            {
              "id": "9f3c-22",
              "data": {
                "type": "approval_approved",
                "approval_project_id": 1, "approval_item_id": 2,
                "item_title": "Petty cash reimbursement",
                "decided_by": "Dana Cruz", "outcome": "approved",
                "decision_comment": "This is approved."
              },
              "created_at": "2026-09-19T08:00:00+08:00"
            }
            """.trimIndent(),
        )

        assertEquals("Dana Cruz approved a request", approved.data.title)
        assertEquals("Petty cash reimbursement", approved.data.body)
    }

    @Test
    fun `an outcome this build does not know keeps the generic heading`() {
        val notification = parse(
            """
            {
              "id": "9f3c-23",
              "data": {
                "type": "approval_escalated",
                "decided_by": "Dana Cruz", "outcome": "escalated_to_board",
                "item_title": "Petty cash reimbursement"
              },
              "created_at": "2026-09-19T08:00:00+08:00"
            }
            """.trimIndent(),
        )

        assertEquals("Approval escalated", notification.data.title)
        assertEquals("Petty cash reimbursement", notification.data.body)
    }

    @Test
    fun `a blocked automation says which rule refused and why`() {
        val notification = parse(
            """
            {
              "id": "9f3c-24",
              "data": {
                "type": "automation_blocked",
                "project_id": 1, "project_name": "sample project",
                "task_id": 30, "task_title": "sample task",
                "rule_name": "complete time trigger",
                "reason": "This task needs at least one attachment before it can be marked Done."
              },
              "created_at": "2026-09-19T08:00:00+08:00"
            }
            """.trimIndent(),
        )

        assertEquals("Automation blocked: complete time trigger", notification.data.title)
        assertEquals(
            "This task needs at least one attachment before it can be marked Done.",
            notification.data.body,
        )
        // It names a task, so tapping it still opens that task.
        assertEquals(NotificationTarget.Task(projectId = 1, taskId = 30), notification.data.target)
    }

    @Test
    fun `a task notification is unaffected`() {
        val notification = parse(
            """
            {
              "id": "9f3c-13",
              "data": {
                "type": "task_assigned",
                "title": "Dana Cruz assigned you a task",
                "task_id": 17, "task_title": "Close the books",
                "project_id": 4, "project_name": "Finance",
                "assigned_by": "Dana Cruz"
              },
              "created_at": "2026-09-19T08:00:00+08:00"
            }
            """.trimIndent(),
        )

        assertEquals(
            NotificationTarget.Task(projectId = 4, taskId = 17),
            notification.data.target,
        )
        assertNull(notification.data.approvalRequestId)
    }
}
