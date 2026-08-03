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
