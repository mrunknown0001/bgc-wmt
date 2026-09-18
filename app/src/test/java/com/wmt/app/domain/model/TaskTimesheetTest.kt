package com.wmt.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules governing what can be done to a timesheet entry.
 *
 * Effort is normally the clock's own work, and the server protects those figures: an
 * entry it generated is corrected by asking, never deleted, and one correction at a time
 * so a reviewer is never choosing between two figures written in ignorance of each other.
 */
class TaskTimesheetTest {

    private fun amendment(
        id: Int = 1,
        kind: AmendmentKind = AmendmentKind.AMEND,
        status: AmendmentStatus = AmendmentStatus.PENDING,
    ) = TimeLogAmendment(
        id = id,
        timeLogId = 77,
        kind = kind,
        status = status,
        loggedOn = "2026-09-17",
        originalMinutes = 60,
        requestedMinutes = 90,
        originalDuration = "1h",
        requestedDuration = "1h 30m",
        reason = "Clock left running",
        requesterName = "Dana",
        reviewerName = null,
        reviewedAt = null,
        reviewNote = null,
    )

    private fun log(
        id: Int = 77,
        generated: Boolean = false,
        pending: TimeLogAmendment? = null,
    ) = TimeLog(
        id = id,
        minutes = 60,
        duration = "1h",
        loggedOn = "2026-09-17",
        note = null,
        userName = "Dana",
        recordedBy = null,
        isGenerated = generated,
        isAmended = false,
        pendingAmendment = pending,
    )

    @Test
    fun `an entry somebody typed can be removed`() {
        assertTrue(log(generated = false).canDelete)
    }

    @Test
    fun `an entry the clock wrote cannot be removed`() {
        // The server refuses this with a 422 telling you to ask for a correction.
        assertFalse(log(generated = true).canDelete)
    }

    @Test
    fun `an entry with nothing pending can be corrected`() {
        assertTrue(log(pending = null).canAmend)
    }

    @Test
    fun `an entry already waiting on a decision takes no second request`() {
        assertFalse(log(pending = amendment()).canAmend)
    }

    @Test
    fun `pending gathers additions and corrections alike`() {
        // Additions hang off no entry, so a sheet that only looked at its logs would
        // leave a reviewer with nothing to decide.
        val sheet = TaskTimesheet(
            logs = listOf(log(id = 1, pending = amendment(id = 10))),
            pendingAdditions = listOf(amendment(id = 20, kind = AmendmentKind.ADD)),
        )

        assertEquals(listOf(20, 10), sheet.pending.map { it.id })
        assertTrue(sheet.hasPending)
    }

    @Test
    fun `a sheet with neither entries nor requests is empty`() {
        assertTrue(TaskTimesheet().isEmpty)
        assertFalse(TaskTimesheet().hasPending)
    }

    @Test
    fun `a sheet holding only an addition request is not empty`() {
        // There is something on screen to decide, even with no entries at all.
        val sheet = TaskTimesheet(
            pendingAdditions = listOf(amendment(kind = AmendmentKind.ADD)),
        )

        assertFalse(sheet.isEmpty)
    }

    @Test
    fun `an addition knows itself as one`() {
        assertTrue(amendment(kind = AmendmentKind.ADD).isAddition)
        assertFalse(amendment(kind = AmendmentKind.AMEND).isAddition)
    }

    @Test
    fun `a decided correction is no longer pending`() {
        assertFalse(amendment(status = AmendmentStatus.APPROVED).isPending)
        assertFalse(amendment(status = AmendmentStatus.REJECTED).isPending)
    }

    @Test
    fun `an unknown kind reads as a correction rather than an addition`() {
        // Adding is the one that writes a new entry, so it is the wrong default.
        assertEquals(AmendmentKind.AMEND, AmendmentKind.from(null))
        assertEquals(AmendmentKind.AMEND, AmendmentKind.from("something-new"))
    }
}
