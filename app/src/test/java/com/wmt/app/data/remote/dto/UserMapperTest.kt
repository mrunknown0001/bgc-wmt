package com.wmt.app.data.remote.dto

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing for GET /api/user, with the capability block that gates the Approvals
 * section.
 *
 * Worth its own test because getting a key name wrong here fails silently and in the
 * safe direction: every capability reads false, the Approvals destination never appears,
 * and nothing anywhere reports an error.
 */
class UserMapperTest {

    private val moshi = Moshi.Builder().build()

    private fun parse(json: String) =
        requireNotNull(moshi.adapter(UserDto::class.java).fromJson(json)).toDomain()

    @Test
    fun `the capability block maps across to the domain user`() {
        val user = parse(
            """
            {
              "id": 9, "name": "Dana Cruz", "email": "dana@bfcgroup.ph",
              "position": "Finance Officer",
              "department": {"id": 2, "name": "Finance"},
              "team": {"id": 5, "name": "Payables"},
              "roles": ["supervisor"],
              "is_active": true,
              "capabilities": {
                "can_approve": true,
                "can_request": true,
                "can_expand_uploads": false,
                "can_create_project": true,
                "can_manage_tasks": true,
                "can_manage_projects": false,
                "can_access_approvals": true
              }
            }
            """.trimIndent(),
        )

        assertEquals("Dana Cruz", user.name)
        assertEquals("Finance", user.department?.name)
        assertEquals("Payables", user.team?.name)
        assertTrue(user.isActive)
        assertTrue(user.capabilities.canApprove)
        assertTrue(user.capabilities.canRequest)
        assertTrue(user.capabilities.canAccessApprovals)
        assertTrue(user.capabilities.canCreateProject)
        assertTrue(user.capabilities.canManageTasks)
        assertFalse(user.capabilities.canExpandUploads)
        assertFalse(user.capabilities.canManageProjects)
        assertTrue(user.capabilities.canUseApprovals)
        assertEquals("DC", user.summary.initials)
    }

    @Test
    fun `an approver without request rights still reaches the section`() {
        val user = parse(
            """
            {"id": 1, "name": "Ana", "email": "a@b.c", "roles": ["admin"],
             "capabilities": {"can_approve": false, "can_request": false,
                              "can_access_approvals": true}}
            """.trimIndent(),
        )

        // can_access_approvals is deliberately wider than can_approve: admins and
        // executives reach approvals without being named approvers.
        assertFalse(user.capabilities.canApprove)
        assertTrue(user.capabilities.canUseApprovals)
    }

    @Test
    fun `a pure requestor reaches the section too`() {
        val user = parse(
            """
            {"id": 2, "name": "Eli", "email": "e@b.c", "roles": [],
             "capabilities": {"can_request": true, "can_access_approvals": false}}
            """.trimIndent(),
        )

        assertTrue(user.capabilities.canUseApprovals)
    }

    @Test
    fun `someone with neither capability does not see the section`() {
        val user = parse(
            """
            {"id": 3, "name": "Sam", "email": "s@b.c", "roles": [],
             "capabilities": {"can_request": false, "can_access_approvals": false}}
            """.trimIndent(),
        )

        assertFalse(user.capabilities.canUseApprovals)
    }

    @Test
    fun `a user cached before capabilities existed stays active with nothing granted`() {
        // The shape the app used to store: no capabilities, no is_active.
        val user = parse(
            """
            {"id": 4, "name": "Old Cache", "email": "o@b.c", "roles": ["user"]}
            """.trimIndent(),
        )

        // Active, because a missing flag must not lock someone out of the whole app...
        assertTrue(user.isActive)
        // ...but no capability is assumed, so no feature is offered that would 403.
        assertFalse(user.capabilities.canUseApprovals)
        assertFalse(user.capabilities.canCreateProject)
    }

    @Test
    fun `the session payload carries the notification switches`() {
        // Verbatim from the deployed server's login response — the block Profile seeds
        // its switches from, so no extra call is needed to paint the screen.
        val dto = requireNotNull(
            moshi.adapter(UserDto::class.java).fromJson(
                """
                {"id": 1, "name": "Admin", "email": "admin@wmt.com", "roles": ["admin"],
                 "notification_preferences": {
                   "email_task_assigned": true, "email_task_due_soon": true,
                   "email_task_due_reminder": true, "email_task_overdue": true,
                   "email_task_comment": true, "email_task_mention": true,
                   "email_comment_deleted": false, "email_task_escalated": true
                 }}
                """.trimIndent(),
            ),
        )

        assertEquals(8, dto.notificationPreferences.size)
        assertEquals(true, dto.notificationPreferences["email_task_assigned"])
        assertEquals(false, dto.notificationPreferences["email_comment_deleted"])
    }

    @Test
    fun `a payload without the switches leaves them to the cached copy`() {
        // Empty rather than null so the seed can be skipped outright: a server or cached
        // payload that omits the block must not wipe switches already stored.
        val dto = requireNotNull(
            moshi.adapter(UserDto::class.java).fromJson(
                """{"id": 1, "name": "Admin", "email": "a@b.c", "roles": []}""",
            ),
        )

        assertTrue(dto.notificationPreferences.isEmpty())
    }

    @Test
    fun `a deactivated account is carried through as inactive`() {
        val user = parse(
            """
            {"id": 5, "name": "Gone", "email": "g@b.c", "roles": [], "is_active": false}
            """.trimIndent(),
        )

        assertFalse(user.isActive)
    }
}
