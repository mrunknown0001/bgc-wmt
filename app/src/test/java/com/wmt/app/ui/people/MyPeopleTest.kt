package com.wmt.app.ui.people

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.dto.MyPersonnelResponse
import com.wmt.app.data.remote.dto.PersonnelOverdueResponse
import com.wmt.app.data.remote.dto.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * My people, parsed from MyPersonnelController's own props. Two things worth pinning:
 * coveredBy is a PHP array keyed by user id, so it arrives as `[]` when nobody is away
 * and as an object otherwise -- and the tree flattens with a unit's own people before
 * the units inside it, which is how the web reads.
 */
class MyPeopleTest {

    private val moshi = Moshi.Builder().build()
    private fun personnel(json: String) = requireNotNull(moshi.adapter(MyPersonnelResponse::class.java).fromJson(json))

    private val tree = """
        "units": [{"type": "department", "id": 2, "name": "Support",
          "members": [{"id": 31, "name": "Desk Bound", "email": "d@x", "position": "Clerk", "role": "user", "open_tasks": 2, "overdue_tasks": 0}],
          "children": [{"type": "team", "id": 5, "name": "Frontline",
            "members": [{"id": 30, "name": "Mel Member", "position": null, "role": "user", "open_tasks": 4, "overdue_tasks": 1}],
            "children": []}]}],
        "viewerId": 9
    """.trimIndent()

    @Test
    fun `nobody away arrives as an empty array and still parses`() {
        val units = personnel("""{$tree, "coveredBy": []}""").toDomain()

        val rows = flatten(units)
        assertEquals(4, rows.size)
        assertTrue(rows.filterIsInstance<PeopleRow.Member>().all { it.member.coveredBy == null })
    }

    @Test
    fun `cover is attached to the person it is about`() {
        val units = personnel(
            """{$tree, "coveredBy": {"30": {"delegates": ["Dee", "Eve"], "period": "10 – 14 Nov"}}}""",
        ).toDomain()

        val members = flatten(units).filterIsInstance<PeopleRow.Member>().associateBy { it.member.id }
        assertEquals("Dee, Eve (10 – 14 Nov)", members.getValue(30).member.coveredBy)
        assertNull(members.getValue(31).member.coveredBy)
    }

    @Test
    fun `a unit's own people come before the units inside it`() {
        val rows = flatten(personnel("""{$tree, "coveredBy": []}""").toDomain())

        val labels = rows.map {
            when (it) {
                is PeopleRow.Unit -> "${it.unit.name}@${it.depth}"
                is PeopleRow.Member -> it.member.name
            }
        }
        assertEquals(listOf("Support@0", "Desk Bound", "Frontline@1", "Mel Member"), labels)
    }

    @Test
    fun `the overdue list says when it is a sample`() {
        val overdue = requireNotNull(
            moshi.adapter(PersonnelOverdueResponse::class.java).fromJson(
                """
                {"tasks": [{"id": 7, "title": "Late one", "status": "to_do", "priority": "high", "due_date": "2026-11-01",
                            "due_time": null, "days_late": 8, "bucket": "month", "url": "/x",
                            "project": null, "assignee": {"id": 30, "name": "Mel Member"}}],
                 "summary": {"total": 640, "people": 12, "projects": 9, "worstDaysLate": 90, "averageDaysLate": 11},
                 "buckets": [{"key": "recent", "label": "1–3 days", "count": 100}],
                 "people": [], "projects": [], "limit": 500, "capped": true}
                """.trimIndent(),
            ),
        ).toDomain()

        assertTrue(overdue.capped)
        assertEquals(500, overdue.limit)
        assertEquals(640, overdue.total)
        val task = overdue.tasks.single()
        assertNull(task.projectId)
        assertEquals("Mel Member", task.assigneeName)
        assertEquals(8, task.daysLate)
    }
}
