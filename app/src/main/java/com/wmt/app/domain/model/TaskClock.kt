package com.wmt.app.domain.model

/** Where a task's clock stands. */
enum class ClockState { NOT_STARTED, RUNNING, PAUSED }

/**
 * A task's clock, and whether it can be worked.
 *
 * Time in motion is not logged effort: it is how long the task has been in flight, less
 * the stretches it spent paused, and it is normally the larger of the two figures.
 */
data class TaskClock(
    val startedAt: String? = null,
    val pausedAt: String? = null,
    val resumedAt: String? = null,
    /** Null until the task has started; counts up while it runs. */
    val timeInMotionMinutes: Int? = null,
    val loggedMinutes: Int? = null,
    val pausedMinutes: Int? = null,
    /** The project switch. With it off the clock is not shown at all. */
    val isVisible: Boolean = false,
    /** Start, pause and resume all refuse on a closed project. */
    val isProjectClosed: Boolean = false,
    /** A finished task cannot be started; the server refuses that too. */
    val isTaskFinished: Boolean = false,
) {
    /**
     * Read the way the server reads it: started and not paused means running. The state
     * is not a column, so deriving it the same way is what keeps the button honest.
     */
    val state: ClockState
        get() = when {
            startedAt == null -> ClockState.NOT_STARTED
            pausedAt != null -> ClockState.PAUSED
            else -> ClockState.RUNNING
        }

    /** Whether any clock button should be offered at all. */
    val canControl: Boolean
        get() = isVisible && !isProjectClosed && !(isTaskFinished && state == ClockState.NOT_STARTED)

    val isRunning: Boolean get() = state == ClockState.RUNNING
}

/**
 * What pausing now would record.
 *
 * [creditedTo] is named only when the work counts against somebody else, which happens
 * when the clock is paused on a task assigned to another person.
 */
data class PausePreview(
    val suggestedMinutes: Int = 0,
    val from: String? = null,
    val alreadyLoggedToday: Int = 0,
    val creditedTo: String? = null,
) {
    /** The server caps a single pause at a day. */
    val maxMinutes: Int get() = MAX_PAUSE_MINUTES

    companion object {
        const val MAX_PAUSE_MINUTES = 1440
    }
}
