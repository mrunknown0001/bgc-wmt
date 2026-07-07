package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** GET /api/mobile/search — { projects: [...], tasks: [...] }. */
@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "projects") val projects: List<SearchProjectDto> = emptyList(),
    @Json(name = "tasks") val tasks: List<SearchTaskDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SearchProjectDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "status") val status: String = "active",
)

/** Compact task hit used by both /api/mobile/search and /api/calendar. */
@JsonClass(generateAdapter = true)
data class SearchTaskDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "project_id") val projectId: Int? = null,
    @Json(name = "project_name") val projectName: String? = null,
    @Json(name = "status") val status: String = "to_do",
    @Json(name = "priority") val priority: String = "medium",
    @Json(name = "due_date") val dueDate: String? = null,
)

/** GET /api/settings. */
@JsonClass(generateAdapter = true)
data class AppSettingsDto(
    @Json(name = "app_name") val appName: String = "WMT",
    @Json(name = "primary_color") val primaryColor: String? = null,
    @Json(name = "max_upload_size") val maxUploadSize: Int = 10,
    @Json(name = "video_max_upload_size") val videoMaxUploadSize: Int = 50,
)

/** GET /api/calendar?month=YYYY-MM — { tasks: [...] }. */
@JsonClass(generateAdapter = true)
data class CalendarResponse(
    @Json(name = "tasks") val tasks: List<SearchTaskDto> = emptyList(),
)

/** GET .../comments?before_id=&limit= — { comments: [...], has_more }. */
@JsonClass(generateAdapter = true)
data class PaginatedCommentsResponse(
    @Json(name = "comments") val comments: List<CommentDto> = emptyList(),
    @Json(name = "has_more") val hasMore: Boolean = false,
)

// ---- Personal to-dos ----

@JsonClass(generateAdapter = true)
data class PersonalTodoDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "title") val title: String = "",
    // Nullable: the create response serializes a fresh model where is_completed
    // can be an explicit null (no default applied yet).
    @Json(name = "is_completed") val isCompleted: Boolean? = null,
    @Json(name = "position") val position: Int? = null,
)

@JsonClass(generateAdapter = true)
data class TodosResponse(
    @Json(name = "todos") val todos: List<PersonalTodoDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TodoResponse(
    @Json(name = "todo") val todo: PersonalTodoDto = PersonalTodoDto(),
)

@JsonClass(generateAdapter = true)
data class CreateTodoRequest(
    @Json(name = "title") val title: String,
)

@JsonClass(generateAdapter = true)
data class UpdateTodoRequest(
    @Json(name = "title") val title: String? = null,
    @Json(name = "is_completed") val isCompleted: Boolean? = null,
)

/** Generic `{ success: true, ... }` acknowledgements (bookmark/archive/todo deletes). */
@JsonClass(generateAdapter = true)
data class SuccessResponse(
    @Json(name = "success") val success: Boolean = true,
)
