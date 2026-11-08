package com.wmt.app.data.remote.dto

import com.squareup.moshi.Moshi
import com.wmt.app.domain.model.ApprovalStatus
import com.wmt.app.domain.model.CorrectionTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The web-page mirrors, parsed from payloads shaped as the server builds them: the
 * SharedRequestsController listing, TimeCorrectionController::data and the approval
 * cover rows. The point is the keys -- these endpoints return the page's own data, so a
 * key that differs from what the page reads is a key the app silently defaults.
 */
class WorkMappersTest {

    private val moshi = Moshi.Builder().build()
    private inline fun <reified T> parse(json: String): T = requireNotNull(moshi.adapter(T::class.java).fromJson(json))

    @Test
    fun `a shared request carries who shared it and whether it is still unread`() {
        val shared = parse<SharedRequestsResponse>(
            """
            {"shares": {"current_page": 1, "last_page": 2, "per_page": 15, "total": 16, "data": [
               {"id": 7, "shared_by": "Ana Cruz", "shared_at": "2026-11-08T09:00:00+08:00", "viewed_at": null,
                "item": {"id": 55, "title": "Expense claim", "series_number": "EXP-0012", "status": "approved",
                         "approval_project_id": 3, "approval_project": {"id": 3, "name": "Expenses"}, "requester": "Bo Lim"}},
               {"id": 8, "shared_by": null, "shared_at": "2026-11-01T09:00:00+08:00", "viewed_at": "2026-11-02T10:00:00+08:00",
                "item": {"id": 56, "title": "Leave", "status": "rejected", "approval_project_id": 4, "approval_project": null}}
             ]},
             "stats": {"total": 16, "unread": 5, "approved": 9, "rejected": 2},
             "projects": [{"id": 3, "name": "Expenses"}],
             "filters": {"search": "", "status": "", "approval_project_id": ""}}
            """.trimIndent(),
        ).toDomain()

        assertEquals(5, shared.unread)
        assertTrue(shared.page.hasMore)
        val (first, second) = shared.page.items
        assertTrue(first.isUnread)
        assertEquals("Ana Cruz", first.sharedBy)
        assertEquals(55, first.requestId)
        assertEquals(3, first.projectId)
        assertEquals("Expenses", first.projectName)
        assertEquals(ApprovalStatus.APPROVED, first.statusEnum)
        assertFalse(second.isUnread)
        assertNull(second.projectName)
    }

    @Test
    fun `the corrections queue reports the tab the server settled on`() {
        val queue = parse<TimeCorrectionsResponse>(
            """
            {"amendments": {"data": [
               {"id": 41, "kind": "change", "status": "pending", "reason": "Timer stopped early.",
                "requester": "Bo", "requested_by": 12, "requested_at": "2026-11-08T09:00:00+08:00",
                "reviewer": null, "reviewed_at": null, "review_note": null,
                "original_duration": "2h", "requested_duration": "3h 30m", "current_duration": "2h",
                "logged_on": "2026-11-07", "note": null,
                "task_id": 902, "task_title": "Install feed lines", "project_id": 74, "project_name": "Fit-out"}],
               "current_page": 1, "last_page": 1, "per_page": 20, "total": 1},
             "projects": [{"id": 74, "name": "Fit-out"}],
             "filters": {"tab": "to_decide", "status": "pending", "project": null},
             "can_decide_any": true,
             "counts": {"to_decide": 1, "mine": 0}}
            """.trimIndent(),
        ).toDomain()

        assertEquals(CorrectionTab.TO_DECIDE, queue.tab)
        assertTrue(queue.canDecideAny)
        assertEquals(1, queue.toDecideCount)
        val row = queue.page.items.single()
        assertTrue(row.isPending)
        assertFalse(row.isAddition)
        assertEquals("3h 30m", row.requestedDuration)
        assertEquals(74, row.projectId)
        assertEquals(902, row.taskId)
    }

    @Test
    fun `asking for to-decide without the right is answered with mine`() {
        val queue = parse<TimeCorrectionsResponse>(
            """{"amendments": {"data": [], "total": 0}, "filters": {"tab": "mine", "status": "pending"}, "can_decide_any": false, "counts": {"to_decide": 0, "mine": 2}}""",
        ).toDomain()

        assertEquals(CorrectionTab.MINE, queue.tab)
        assertFalse(queue.canDecideAny)
    }

    @Test
    fun `cover rows say whose they are and who may end them`() {
        val response = parse<DelegationsResponse>(
            """
            {"delegations": [
              {"id": 3, "user": {"id": 12, "name": "Me"}, "delegate": {"id": 40, "name": "Dee"},
               "starts_on": "2026-11-10", "ends_on": "2026-11-14", "period": "10 – 14 Nov 2026",
               "reason": "Leave", "active": false, "is_mine": true, "can_manage": true}],
             "can_manage_others": false}
            """.trimIndent(),
        )
        val cover = response.delegations.single().toDomain()

        assertTrue(cover.isMine)
        assertTrue(cover.canManage)
        assertEquals("Dee", cover.delegateName)
        assertEquals("10 – 14 Nov 2026", cover.period)
        assertFalse(response.canManageOthers)
    }
}
