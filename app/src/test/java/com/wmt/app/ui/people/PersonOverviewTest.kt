package com.wmt.app.ui.people

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.dto.PersonOverviewResponse
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One person's overview, parsed from a payload shaped as the server's
 * UserController::overview() builds it -- the same method the web's person page renders
 * from, so a key spelled differently here is a figure the phone silently shows as zero.
 */
class PersonOverviewTest {

    private val moshi = Moshi.Builder().build()

    private fun parse(json: String) =
        requireNotNull(moshi.adapter(PersonOverviewResponse::class.java).fromJson(json)).toDomain()

    private val payload = """
        {"profile": {"id": 17, "name": "Mel Member", "email": "mel@example.com", "position": "Technician",
                     "is_active": true, "department": "Support", "division": "Field", "team": "Frontline",
                     "roles": ["user"]},
         "filtered": null,
         "delegatedAway": [
            {"task": {"id": 90, "title": "Covered one", "status": "to_do", "priority": "low", "url": "/tasks/90/edit",
                      "project": {"id": 4, "name": "Ops"}},
             "delegate": "Dee Stand", "ends_on": "2026-11-20", "delegation_id": 3, "per_task": true},
            {"task": null, "delegate": "Gone", "ends_on": null}
         ],
         "currentCover": {"id": 5, "running": true, "period": "10 Nov – 20 Nov", "starts_on": "2026-11-10",
                          "ends_on": "2026-11-20", "delegates": ["Dee Stand", "Ray Two"]},
         "kpis": {"projectsOwned": 0, "projectsMember": 2, "projectsInvolved": 3, "tasksTotal": 12,
                  "tasksCompleted": 7, "tasksActive": 5, "tasksOverdue": 2, "completionRate": 58,
                  "onTimeRate": 71, "productivity": 60, "activity30": 14, "lastActivityAt": "2026-11-08T09:00:00.000000Z"},
         "recentActivity": [
            {"action": "updated", "entity": "Pump check", "description": "Changed status to Done", "created_at": "2026-11-08T09:00:00.000000Z"},
            {"action": "created", "entity": "Valve log", "description": null, "created_at": "2026-11-07T09:00:00.000000Z"},
            {"action": null, "entity": null, "description": "", "created_at": null}
         ],
         "upcoming": {
            "tasks": [
               {"id": 1, "title": "Today", "status": "in_progress", "priority": "high", "due_date": "2026-11-09",
                "due_time": null, "url": "/x", "project": {"id": 4, "name": "Ops"}, "in_week": true, "in_month": true},
               {"id": 2, "title": "Later", "status": "to_do", "priority": "medium", "due_date": "2026-11-25",
                "due_time": null, "url": "/y", "project": null, "in_week": false, "in_month": true}
            ],
            "overdue": [
               {"id": 3, "title": "Late", "status": "to_do", "priority": "urgent", "due_date": "2026-11-01",
                "due_time": "09:00", "url": "/z", "project": {"id": 4, "name": "Ops"}, "days_late": 8}
            ],
            "weekCount": 1, "monthCount": 2, "overdueCount": 2,
            "weekEnds": "2026-11-14", "monthEnds": "2026-11-30", "limit": 50
         }}
    """.trimIndent()

    @Test
    fun `the page's figures and lists come across under the page's own keys`() {
        val person = parse(payload)

        assertEquals("Mel Member", person.name)
        assertEquals("Technician", person.position)
        assertEquals("Frontline · Support · Field", person.placement)
        assertEquals(5, person.openTasks)
        assertEquals(2, person.overdueTasks)
        assertEquals(58, person.completionRate)
        assertEquals(71, person.onTimeRate)
        assertEquals(3, person.projectsInvolved)
        assertEquals(14, person.activity30)

        val late = person.overdue.single()
        assertEquals(8, late.daysLate)
        assertEquals(4, late.projectId)
        assertNull("a personal task has no project", person.upcoming[1].projectId)
        assertEquals(TaskStatus.IN_PROGRESS, person.upcoming[0].statusEnum)
    }

    @Test
    fun `cover reads as who holds the work, and a stand-in row without its task is dropped`() {
        val person = parse(payload)

        assertEquals("Dee Stand, Ray Two (10 Nov – 20 Nov)", person.cover)
        assertTrue(person.coverRunning)
        assertEquals(listOf(90), person.delegatedAway.map { it.task.id })
        assertEquals("Dee Stand", person.delegatedAway.single().delegate)
    }

    @Test
    fun `activity falls back to what was touched, and a row with nothing to say is dropped`() {
        val activity = parse(payload).recentActivity

        assertEquals(listOf("Changed status to Done", "created Valve log"), activity.map { it.description })
    }

    @Test
    fun `an overview with nothing in it still parses, with no cover`() {
        val person = parse(
            """{"profile": {"id": 2, "name": "New Hire", "position": "", "is_active": false, "roles": []},
                "filtered": null, "delegatedAway": [], "currentCover": null, "recentActivity": [],
                "kpis": {}, "upcoming": {"tasks": [], "overdue": [], "weekCount": 0, "monthCount": 0, "overdueCount": 0, "limit": 50}}""",
        )

        assertNull(person.position)
        assertNull(person.placement)
        assertNull(person.cover)
        assertFalse(person.isActive)
        assertEquals(WorkBucket.WEEK, person.defaultBucket())
    }

    @Test
    fun `the week is the month's rows marked in the week, and the counts are the server's`() {
        val person = parse(payload)

        assertEquals(WorkBucket.OVERDUE, person.defaultBucket())
        assertEquals(listOf(1), person.bucket(WorkBucket.WEEK).tasks.map { it.id })
        assertEquals(listOf(1, 2), person.bucket(WorkBucket.MONTH).tasks.map { it.id })

        // Two overdue, one row sent: the list is a sample and must say so.
        val overdue = person.bucket(WorkBucket.OVERDUE)
        assertEquals(2, overdue.count)
        assertTrue(overdue.capped)
        assertFalse(person.bucket(WorkBucket.MONTH).capped)
    }
}
