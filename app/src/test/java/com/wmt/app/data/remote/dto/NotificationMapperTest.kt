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

    // The body is left empty on purpose: a populated one is flattened through
    // android.text.Html, which a JVM unit test has no implementation of.
    @Test
    fun `a task notification is unaffected`() {
        val notification = parse(
            """
            {
              "id": "9f3c-13",
              "data": {
                "type": "task_assigned",
                "title": "Dana Cruz assigned you a task",
                "body": "",
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
