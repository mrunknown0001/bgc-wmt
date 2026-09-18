package com.wmt.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the task clock.
 *
 * The state is not a column: the server decides a task is running when it has started
 * and is not paused, and the button has to reach the same answer or it offers a press
 * the server refuses.
 */
class TaskClockTest {

    private val started = "2026-09-18T01:00:00.000000Z"
    private val paused = "2026-09-18T03:00:00.000000Z"

    @Test
    fun `a clock that never started is not started`() {
        val clock = TaskClock(startedAt = null, isVisible = true)

        assertEquals(ClockState.NOT_STARTED, clock.state)
        assertFalse(clock.isRunning)
    }

    @Test
    fun `started and not paused is running`() {
        val clock = TaskClock(startedAt = started, pausedAt = null, isVisible = true)

        assertEquals(ClockState.RUNNING, clock.state)
        assertTrue(clock.isRunning)
    }

    @Test
    fun `a pause time means paused, whatever else is set`() {
        // A resume time from an earlier stretch must not read as running again.
        val clock = TaskClock(
            startedAt = started,
            pausedAt = paused,
            resumedAt = "2026-09-18T02:00:00.000000Z",
            isVisible = true,
        )

        assertEquals(ClockState.PAUSED, clock.state)
    }

    @Test
    fun `the strip is hidden entirely when the project does not use it`() {
        val clock = TaskClock(startedAt = started, isVisible = false)

        assertFalse("no controls without the project switch", clock.canControl)
    }

    @Test
    fun `a closed project offers no controls`() {
        // Start, pause and resume all answer 422 there, so nothing is offered.
        val clock = TaskClock(startedAt = started, isVisible = true, isProjectClosed = true)

        assertFalse(clock.canControl)
    }

    @Test
    fun `a finished task cannot have its clock started`() {
        val clock = TaskClock(startedAt = null, isVisible = true, isTaskFinished = true)

        assertFalse(clock.canControl)
    }

    @Test
    fun `a finished task that is still running can still be paused`() {
        // Marking something done while the clock runs is ordinary; the time still has
        // to be recorded, and only start is refused on a finished task.
        val clock = TaskClock(startedAt = started, isVisible = true, isTaskFinished = true)

        assertEquals(ClockState.RUNNING, clock.state)
        assertTrue(clock.canControl)
    }

    @Test
    fun `controls are offered on a running clock in an open project`() {
        val clock = TaskClock(startedAt = started, isVisible = true)

        assertTrue(clock.canControl)
    }
}
