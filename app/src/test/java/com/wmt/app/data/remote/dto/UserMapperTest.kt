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
    fun `a deactivated account is carried through as inactive`() {
        val user = parse(
            """
            {"id": 5, "name": "Gone", "email": "g@b.c", "roles": [], "is_active": false}
            """.trimIndent(),
        )

        assertFalse(user.isActive)
    }
}
