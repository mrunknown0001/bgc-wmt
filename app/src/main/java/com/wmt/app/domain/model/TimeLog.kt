package com.wmt.app.domain.model

/** What a correction is asking for. */
enum class AmendmentKind(val raw: String) {
    /** Change what an existing entry says. */
    AMEND("amend"),

    /** Add an entry on a day that has none. */
    ADD("add");

    companion object {
        fun from(raw: String?): AmendmentKind = if (raw == ADD.raw) ADD else AMEND
    }
}

enum class AmendmentStatus(val raw: String, val label: String) {
    PENDING("pending", "Waiting"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected");

    companion object {
        fun from(raw: String?): AmendmentStatus =
            entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

/**
 * One day's effort against a task.
 *
 * Most entries are written by the clock rather than typed by anyone, which is why
 * [isGenerated] matters: those cannot be deleted, only argued with.
 */
data class TimeLog(
    val id: Int,
    val minutes: Int,
    /** Preformatted server-side; "none" for a deliberate zero rather than a missing figure. */
    val duration: String?,
    val loggedOn: String?,
    val note: String?,
    val userName: String?,
    /** Named only when somebody recorded this on another person's behalf. */
    val recordedBy: String?,
    val isGenerated: Boolean,
    /** An amended figure should not pass for an untouched one. */
    val isAmended: Boolean,
    val pendingAmendment: TimeLogAmendment?,
) {
    /** The clock's own entries are corrected, never removed. */
    val canDelete: Boolean get() = !isGenerated

    /** One correction at a time: a second would leave a reviewer choosing blind. */
    val canAmend: Boolean get() = pendingAmendment == null
}

/** A request to change what the timesheet says, and how it was decided. */
data class TimeLogAmendment(
    val id: Int,
    val timeLogId: Int?,
    val kind: AmendmentKind,
    val status: AmendmentStatus,
    val loggedOn: String?,
    val originalMinutes: Int,
    val requestedMinutes: Int,
    val originalDuration: String?,
    val requestedDuration: String?,
    val reason: String?,
    val requesterName: String?,
    val reviewerName: String?,
    val reviewedAt: String?,
    val reviewNote: String?,
) {
    val isPending: Boolean get() = status == AmendmentStatus.PENDING

    val isAddition: Boolean get() = kind == AmendmentKind.ADD
}

/**
 * A task's timesheet: what has been recorded, what is waiting on a decision, and what
 * this reader is allowed to do about either.
 */
data class TaskTimesheet(
    val logs: List<TimeLog> = emptyList(),
    /** Requests for a day with no entry, which hang off no log of their own. */
    val pendingAdditions: List<TimeLogAmendment> = emptyList(),
    val totalMinutes: Int = 0,
    val estimatedMinutes: Int? = null,
    /** Whether this reader decides corrections rather than only asking for them. */
    val canReview: Boolean = false,
    /** False on a standalone task: no project means nobody to decide a correction. */
    val amendmentsAvailable: Boolean = false,
    /** The server's date, used when offering a day to log against. */
    val today: String? = null,
) {
    val isEmpty: Boolean get() = logs.isEmpty() && pendingAdditions.isEmpty()

    /** Everything still waiting on a decision, additions included. */
    val pending: List<TimeLogAmendment>
        get() = pendingAdditions + logs.mapNotNull { it.pendingAmendment }

    val hasPending: Boolean get() = pending.isNotEmpty()
}

/** The outcome of asking for a correction. */
data class AmendmentOutcome(
    val amendment: TimeLogAmendment,
    /**
     * True when the person asking also decides corrections here, so it took effect at
     * once and nothing is waiting.
     */
    val applied: Boolean,
    val totalMinutes: Int?,
)
