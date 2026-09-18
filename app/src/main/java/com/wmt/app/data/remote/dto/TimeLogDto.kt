package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Wire shapes for a task's timesheet and its corrections.
 *
 * Effort is normally worked out from the clock, so most entries here were written by the
 * server rather than by a person. Changing one is a request that somebody decides, which
 * is why an amendment is a record in its own right rather than an edit.
 */

/** GET /api/tasks/{task}/time-logs. */
@JsonClass(generateAdapter = true)
data class TimeLogsResponse(
    @Json(name = "logs") val logs: List<TimeLogDto> = emptyList(),
    /**
     * Requests for an entry on a day that has none. They hang off no log, so they would
     * otherwise never appear, leaving a reviewer nothing to decide.
     */
    @Json(name = "pending_additions") val pendingAdditions: List<TimeLogAmendmentDto> = emptyList(),
    @Json(name = "total_minutes") val totalMinutes: Int = 0,
    @Json(name = "estimated_minutes") val estimatedMinutes: Int? = null,
    /** Whether this reader decides corrections, rather than only asking for them. */
    @Json(name = "can_review_amendments") val canReviewAmendments: Boolean = false,
    /** A standalone task has no project, so no one to decide a correction. */
    @Json(name = "amendments_available") val amendmentsAvailable: Boolean = false,
    /** The server's date, not the phone's: the two disagree for most of the day in Manila. */
    @Json(name = "today") val today: String? = null,
)

@JsonClass(generateAdapter = true)
data class TimeLogDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "task_id") val taskId: Int? = null,
    @Json(name = "task_title") val taskTitle: String? = null,
    @Json(name = "project_id") val projectId: Int? = null,
    /** A name, not an object. */
    @Json(name = "user") val user: String? = null,
    @Json(name = "user_id") val userId: Int? = null,
    /** Set only when somebody entered this for another person. */
    @Json(name = "recorded_by") val recordedBy: String? = null,
    @Json(name = "minutes") val minutes: Int = 0,
    /** Preformatted by the server, and "none" rather than a dash for a deliberate zero. */
    @Json(name = "duration") val duration: String? = null,
    @Json(name = "logged_on") val loggedOn: String? = null,
    @Json(name = "note") val note: String? = null,
    /** Where the figure came from, so a clock total reads differently from a stated one. */
    @Json(name = "source") val source: String? = null,
    /** Written by the clock and never argued with: these cannot be deleted, only corrected. */
    @Json(name = "generated") val generated: Boolean = false,
    @Json(name = "pending_amendment") val pendingAmendment: TimeLogAmendmentDto? = null,
    @Json(name = "amended") val amended: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class TimeLogAmendmentDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "time_log_id") val timeLogId: Int? = null,
    /** "amend" changes an entry; "add" asks for one on a day that has none. */
    @Json(name = "kind") val kind: String? = null,
    @Json(name = "logged_on") val loggedOn: String? = null,
    @Json(name = "status") val status: String = "pending",
    @Json(name = "original_minutes") val originalMinutes: Int = 0,
    @Json(name = "requested_minutes") val requestedMinutes: Int = 0,
    @Json(name = "original_duration") val originalDuration: String? = null,
    @Json(name = "requested_duration") val requestedDuration: String? = null,
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "requested_by") val requestedBy: Int? = null,
    @Json(name = "requester") val requester: String? = null,
    @Json(name = "reviewer") val reviewer: String? = null,
    @Json(name = "reviewed_at") val reviewedAt: String? = null,
    @Json(name = "review_note") val reviewNote: String? = null,
)

/**
 * Asking for an entry to say something else.
 *
 * Duration is a string, not minutes: the server parses "1.5", "1:30" and "90m" alike,
 * and sending a number would throw away what the person actually typed.
 */
@JsonClass(generateAdapter = true)
data class AmendTimeLogRequest(
    @Json(name = "duration") val duration: String,
    @Json(name = "reason") val reason: String,
)

/** Asking for an entry on a day the clock never ran. */
@JsonClass(generateAdapter = true)
data class AddTimeLogRequest(
    @Json(name = "duration") val duration: String,
    /** yyyy-MM-dd, and the server refuses a future date. */
    @Json(name = "logged_on") val loggedOn: String,
    @Json(name = "reason") val reason: String,
)

/** Approving or rejecting a correction; the note is shown to whoever asked. */
@JsonClass(generateAdapter = true)
data class ReviewAmendmentRequest(
    @Json(name = "note") val note: String? = null,
)

/**
 * The answer to raising a correction. [applied] is true when the person asking also
 * decides corrections here, in which case it already took effect and there is nothing
 * pending.
 */
@JsonClass(generateAdapter = true)
data class AmendmentResponse(
    @Json(name = "amendment") val amendment: TimeLogAmendmentDto = TimeLogAmendmentDto(),
    @Json(name = "applied") val applied: Boolean = false,
    @Json(name = "total_minutes") val totalMinutes: Int? = null,
    /** Present on a decision: what the entry now says. */
    @Json(name = "minutes") val minutes: Int? = null,
)

/** DELETE /api/time-logs/{id}. */
@JsonClass(generateAdapter = true)
data class TimeLogDeletedResponse(
    @Json(name = "total_minutes") val totalMinutes: Int = 0,
)
