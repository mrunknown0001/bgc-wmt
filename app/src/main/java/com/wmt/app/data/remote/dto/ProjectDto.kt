package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProjectDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "status") val status: String = "active",
    @Json(name = "owner") val owner: UserSummaryDto? = null,
    @Json(name = "due_date") val dueDate: String? = null,
    @Json(name = "tasks_count") val tasksCount: Int = 0,
    @Json(name = "completed_tasks_count") val completedTasksCount: Int = 0,
    @Json(name = "members") val members: List<UserSummaryDto> = emptyList(),
)

/** Body for POST /api/projects. */
@JsonClass(generateAdapter = true)
data class CreateProjectRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "status") val status: String = "active",
    @Json(name = "due_date") val dueDate: String? = null,
)

/** POST /api/projects response: { project }. */
@JsonClass(generateAdapter = true)
data class ProjectResponse(
    @Json(name = "project") val project: ProjectDto = ProjectDto(),
)

/** A section/column within a project. Tasks are returned in a sibling array keyed by section_id. */
@JsonClass(generateAdapter = true)
data class ProjectSectionDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "position") val position: Int = 0,
)

/** GET /api/projects/{id} — { project, tasks (flat, with section_id), sections }. */
@JsonClass(generateAdapter = true)
data class ProjectDetailResponse(
    @Json(name = "project") val project: ProjectDto = ProjectDto(),
    @Json(name = "tasks") val tasks: List<TaskDto> = emptyList(),
    @Json(name = "sections") val sections: List<ProjectSectionDto> = emptyList(),
)
