package com.wmt.app.ui.inbox

import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.model.NotificationData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The inbox is paged and polled at the same time, which is where it can go wrong: the
 * poll only ever fetches page one, so it must not throw away pages the user scrolled
 * to, and a notification that arrived since must not appear twice because everything
 * below it shifted down a slot.
 */
class InboxPagingTest {

    private fun n(id: String) = Notification(
        id = id,
        data = NotificationData(
            title = "Task assigned",
            body = "Reconcile petty cash",
            type = "task_assigned",
            taskId = 7,
            projectId = 3,
        ),
        readAt = null,
        createdAt = "2026-09-20T09:00:00+08:00",
    )

    private fun ids(list: List<Notification>) = list.map { it.id }

    @Test
    fun `a poll with only page one on screen simply replaces it`() {
        val merged = InboxPaging.mergeFirstPage(
            fresh = listOf(n("new"), n("a"), n("b")),
            existing = listOf(n("a"), n("b"), n("c")),
            keepPagedTail = false,
        )

        assertEquals(listOf("new", "a", "b"), ids(merged))
    }

    @Test
    fun `a poll keeps the pages scrolled to below the fresh first page`() {
        val paged = listOf(n("a"), n("b"), n("c"), n("d"))

        val merged = InboxPaging.mergeFirstPage(
            // "new" arrived, so the server's page one now ends one item earlier.
            fresh = listOf(n("new"), n("a"), n("b")),
            existing = paged,
            keepPagedTail = true,
        )

        // Nothing the user had scrolled to is lost, and nothing is listed twice.
        assertEquals(listOf("new", "a", "b", "c", "d"), ids(merged))
    }

    @Test
    fun `an item that slipped onto a later page is not duplicated`() {
        val merged = InboxPaging.mergeFirstPage(
            fresh = listOf(n("a"), n("b")),
            // "b" had already been paged in as part of the second page.
            existing = listOf(n("a"), n("x"), n("b")),
            keepPagedTail = true,
        )

        assertEquals(listOf("a", "b", "x"), ids(merged))
    }

    @Test
    fun `the next page lands under what is already listed`() {
        val appended = InboxPaging.appendPage(
            existing = listOf(n("a"), n("b")),
            next = listOf(n("c"), n("d")),
        )

        assertEquals(listOf("a", "b", "c", "d"), ids(appended))
    }

    @Test
    fun `a next page that overlaps the list adds only what is new`() {
        val appended = InboxPaging.appendPage(
            existing = listOf(n("a"), n("b"), n("c")),
            // A new arrival shifted the window, so "c" comes back on page two.
            next = listOf(n("c"), n("d")),
        )

        assertEquals(listOf("a", "b", "c", "d"), ids(appended))
    }

    @Test
    fun `a page of nothing new leaves the list untouched`() {
        val existing = listOf(n("a"), n("b"))

        assertEquals(existing, InboxPaging.appendPage(existing, listOf(n("a"))))
        assertEquals(existing, InboxPaging.appendPage(existing, emptyList()))
    }
}
