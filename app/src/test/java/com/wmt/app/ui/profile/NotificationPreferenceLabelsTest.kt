package com.wmt.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The switch list is built from whatever keys the server sends, so the two things that
 * can go wrong are a key the app has never heard of disappearing from the screen, and a
 * key the server stopped sending leaving a switch that writes to nothing.
 */
class NotificationPreferenceLabelsTest {

    /** The block the deployed server actually returns, in its own key order. */
    private val live = mapOf(
        "email_task_assigned" to true,
        "email_task_due_soon" to true,
        "email_task_due_reminder" to true,
        "email_task_overdue" to true,
        "email_task_comment" to true,
        "email_task_mention" to true,
        "email_comment_deleted" to false,
        "email_task_escalated" to true,
    )

    @Test
    fun `every live key gets a written label and its own state`() {
        val rows = NotificationPreferenceLabels.ordered(live)

        assertEquals(live.size, rows.size)
        assertTrue(rows.none { it.label.contains('_') })
        assertEquals("Task assigned to me", rows.first { it.key == "email_task_assigned" }.label)
        assertTrue(rows.first { it.key == "email_task_assigned" }.enabled)
        // The one default-off switch on the server.
        assertTrue(rows.none { it.key == "email_comment_deleted" && it.enabled })
    }

    @Test
    fun `the written order holds regardless of the order the server sends`() {
        val rows = NotificationPreferenceLabels.ordered(live.entries.reversed().associate { it.toPair() })

        assertEquals(
            listOf(
                "email_task_assigned",
                "email_task_due_soon",
                "email_task_due_reminder",
                "email_task_overdue",
                "email_task_comment",
                "email_task_mention",
                "email_task_escalated",
                "email_comment_deleted",
            ),
            rows.map { it.key },
        )
    }

    @Test
    fun `a key the app has never seen still renders, after the known ones`() {
        val rows = NotificationPreferenceLabels.ordered(
            live + mapOf("email_weekly_digest" to true, "email_automation_blocked" to false),
        )

        assertEquals(live.size + 2, rows.size)
        // Unknown keys land at the end, alphabetically, prettified from the key itself.
        assertEquals(
            listOf("email_automation_blocked", "email_weekly_digest"),
            rows.takeLast(2).map { it.key },
        )
        assertEquals("Weekly digest", rows.last().label)
    }

    @Test
    fun `a key the server stopped sending gets no switch`() {
        val rows = NotificationPreferenceLabels.ordered(live - "email_task_overdue")

        assertTrue(rows.none { it.key == "email_task_overdue" })
        assertEquals(live.size - 1, rows.size)
    }

    @Test
    fun `nothing cached yet means nothing to show`() {
        assertTrue(NotificationPreferenceLabels.ordered(emptyMap()).isEmpty())
    }
}
