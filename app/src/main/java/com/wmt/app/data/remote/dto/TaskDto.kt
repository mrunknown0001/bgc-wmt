package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TaskDto(
    @Json(name = "id") val id: Int = 0,
    // Nullable defensively: some server responses have emitted an explicit
    // `"project_id": null`, and a non-null Int makes Moshi reject the whole payload.
    @Json(name = "project_id") val projectId: Int? = null,
    @Json(name = "section_id") val sectionId: Int? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "status") val status: String = "backlog",
    @Json(name = "priority") val priority: String = "medium",
    @Json(name = "assignee") val assignee: UserSummaryDto? = null,
    @Json(name = "due_date") val dueDate: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "project") val project: ProjectSummaryDto? = null,
    @Json(name = "subtasks_count") val subtasksCount: Int = 0,
    @Json(name = "completed_subtasks_count") val completedSubtasksCount: Int = 0,
    @Json(name = "is_recurring") val isRecurring: Boolean = false,
    @Json(name = "recurrence_frequency") val recurrenceFrequency: String? = null,
    @Json(name = "recurrence_interval") val recurrenceInterval: Int? = null,
    @Json(name = "collaborators") val collaborators: List<UserSummaryDto> = emptyList(),
    @Json(name = "creator") val creator: UserSummaryDto? = null,

    // The task clock. These are plain columns on the model, so they ride along with
    // every task payload; running versus paused is read from them the way the server
    // reads it: started and not paused means running.
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "motion_paused_at") val motionPausedAt: String? = null,
    @Json(name = "motion_resumed_at") val motionResumedAt: String? = null,
    @Json(name = "motion_paused_minutes") val motionPausedMinutes: Int? = null,
)

@JsonClass(generateAdapter = true)
data class CommentDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "body") val body: String = "",
    @Json(name = "user") val user: UserSummaryDto? = null,
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "attachments") val attachments: List<AttachmentDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AttachmentDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "file_name") val fileName: String = "",
    @Json(name = "file_type") val fileType: String = "",
    @Json(name = "file_size") val fileSize: Long = 0,
    @Json(name = "url") val url: String = "",
)

@JsonClass(generateAdapter = true)
data class ActivityDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "field") val field: String? = null,
    @Json(name = "old_value") val oldValue: String? = null,
    @Json(name = "new_value") val newValue: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "user") val user: UserSummaryDto? = null,
    @Json(name = "created_at") val createdAt: String = "",
)

/** GET /api/projects/{projectId}/tasks/{taskId} — { task, comments, activities }. */
@JsonClass(generateAdapter = true)
data class TaskDetailResponse(
    @Json(name = "task") val task: TaskDto = TaskDto(),
    @Json(name = "comments") val comments: List<CommentDto> = emptyList(),
    @Json(name = "activities") val activities: List<ActivityDto> = emptyList(),
    @Json(name = "members") val members: List<UserSummaryDto> = emptyList(),
    @Json(name = "subtasks") val subtasks: List<TaskDto> = emptyList(),

    // Clock figures, camelCase because this payload is hand-built rather than a model.
    @Json(name = "timeInMotionMinutes") val timeInMotionMinutes: Int? = null,
    @Json(name = "loggedMinutes") val loggedMinutes: Int? = null,
    @Json(name = "pausedMinutes") val pausedMinutes: Int? = null,
    /**
     * The project switch. The clock strip hides entirely when it is off, and the
     * start, pause and resume routes all 422 on a closed project, so both are needed
     * before a button is offered rather than after it is refused.
     */
    @Json(name = "showTimeInMotion") val showTimeInMotion: Boolean = false,
    @Json(name = "projectIsClosed") val projectIsClosed: Boolean = false,
)

/** PATCH /api/projects/{projectId}/tasks/{taskId}/patch — { success, task }. */
@JsonClass(generateAdapter = true)
data class TaskPatchResponse(
    @Json(name = "task") val task: TaskDto = TaskDto(),
)

/** POST /api/projects/{projectId}/tasks response: { task }. */
@JsonClass(generateAdapter = true)
data class TaskResponse(
    @Json(name = "task") val task: TaskDto = TaskDto(),
)

/** Body for POST /api/projects/{projectId}/tasks. */
@JsonClass(generateAdapter = true)
data class CreateTaskRequest(
    @Json(name = "title") val title: String,
    @Json(name = "status") val status: String = "to_do",
    @Json(name = "priority") val priority: String = "medium",
    @Json(name = "description") val description: String? = null,
    @Json(name = "assigned_to") val assignedTo: Int? = null,
    @Json(name = "section_id") val sectionId: Int? = null,
    @Json(name = "due_date") val dueDate: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "is_recurring") val isRecurring: Boolean = false,
    @Json(name = "recurrence_frequency") val recurrenceFrequency: String? = null,
    @Json(name = "recurrence_interval") val recurrenceInterval: Int? = null,
    @Json(name = "collaborator_ids") val collaboratorIds: List<Int>? = null,
)

/** Body for PUT /api/projects/{projectId}/tasks/{taskId} (title/status/priority required). */
@JsonClass(generateAdapter = true)
data class UpdateTaskRequest(
    @Json(name = "title") val title: String,
    @Json(name = "status") val status: String,
    @Json(name = "priority") val priority: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "assigned_to") val assignedTo: Int? = null,
    @Json(name = "due_date") val dueDate: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "is_recurring") val isRecurring: Boolean = false,
    @Json(name = "recurrence_frequency") val recurrenceFrequency: String? = null,
    @Json(name = "recurrence_interval") val recurrenceInterval: Int? = null,
    @Json(name = "collaborator_ids") val collaboratorIds: List<Int>? = null,
)

/** GET /api/my-tasks — { taskGroups: { overdue, dueToday, ... }, stats }. */
@JsonClass(generateAdapter = true)
data class MyTasksResponse(
    @Json(name = "taskGroups") val taskGroups: TaskGroupsDto = TaskGroupsDto(),
)

@JsonClass(generateAdapter = true)
data class TaskGroupsDto(
    @Json(name = "overdue") val overdue: List<TaskDto> = emptyList(),
    @Json(name = "dueToday") val dueToday: List<TaskDto> = emptyList(),
    @Json(name = "upcoming") val upcoming: List<TaskDto> = emptyList(),
    @Json(name = "later") val later: List<TaskDto> = emptyList(),
    @Json(name = "noDueDate") val noDueDate: List<TaskDto> = emptyList(),
) {
    /** Flattened in display order; the UI re-groups by due date itself. */
    fun all(): List<TaskDto> = overdue + dueToday + upcoming + later + noDueDate
}

@JsonClass(generateAdapter = true)
data class PatchRequest(
    @Json(name = "field") val field: String,
    @Json(name = "value") val value: String,
)

@JsonClass(generateAdapter = true)
data class CommentRequest(
    @Json(name = "body") val body: String,
)

/**
 * What the clock routes answer with.
 *
 * Start reports the status it moved the task to; pause and resume report the motion
 * columns and, for a pause, the minutes just written. One shape covers both, since each
 * only fills in the half it knows about.
 */
@JsonClass(generateAdapter = true)
data class TaskClockDto(
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "motion_paused_at") val motionPausedAt: String? = null,
    @Json(name = "motion_resumed_at") val motionResumedAt: String? = null,
    @Json(name = "motion_paused_minutes") val motionPausedMinutes: Int? = null,
    @Json(name = "time_in_motion_minutes") val timeInMotionMinutes: Int? = null,
    /** Only on start: pressing it also moves the task to in_progress. */
    @Json(name = "status") val status: String? = null,
    /** Only on pause: the minutes recorded against the day. */
    @Json(name = "logged_minutes") val loggedMinutes: Int? = null,
)

/**
 * GET .../pause-preview — what the server would record if the clock were paused now.
 *
 * Read before showing the pause dialog so the figure offered is the server's own
 * suggestion rather than something counted on the phone.
 */
@JsonClass(generateAdapter = true)
data class PausePreviewDto(
    @Json(name = "suggested_minutes") val suggestedMinutes: Int = 0,
    @Json(name = "from") val from: String? = null,
    @Json(name = "already_logged_today") val alreadyLoggedToday: Int = 0,
    /**
     * Whose day this counts against, named only when it is not the person pausing:
     * pausing somebody else's task records their work, not yours.
     */
    @Json(name = "credited_to") val creditedTo: String? = null,
)

/** PATCH .../pause — minutes are required and capped at a day. */
@JsonClass(generateAdapter = true)
data class PauseTaskRequest(
    @Json(name = "minutes") val minutes: Int,
    @Json(name = "note") val note: String? = null,
)
