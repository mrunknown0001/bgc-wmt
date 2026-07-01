package com.wmt.app.domain.model

data class Task(
    val id: Int,
    val projectId: Int,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val assignee: UserSummary?,
    val dueDate: String?,
    val startDate: String?,
    val project: ProjectSummary?,
    val subtasksCount: Int,
    val completedSubtasksCount: Int,
) {
    val statusEnum: TaskStatus get() = TaskStatus.from(status)
    val priorityEnum: TaskPriority get() = TaskPriority.from(priority)
}

data class Comment(
    val id: Int,
    val body: String,
    val author: UserSummary?,
    val createdAt: String,
    val attachments: List<Attachment> = emptyList(),
)

data class Attachment(
    val id: Int,
    val fileName: String,
    val fileType: String,
    val fileSizeBytes: Long,
    val url: String,
) {
    val isImage: Boolean get() = fileType.startsWith("image/")
}

/** An entry in a task's activity timeline (optional, surfaced when present). */
data class Activity(
    val id: Int,
    val description: String,
    val actor: UserSummary?,
    val createdAt: String,
)

/** Aggregate task view backing the Task Detail screen. */
data class TaskDetail(
    val task: Task,
    val comments: List<Comment>,
    val activities: List<Activity>,
    /** Users who can be @mentioned in comments (project members + owner). */
    val members: List<UserSummary> = emptyList(),
    val subtasks: List<Task> = emptyList(),
)
