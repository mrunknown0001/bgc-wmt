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
    val completedAt: String? = null,
    val project: ProjectSummary?,
    val subtasksCount: Int,
    val completedSubtasksCount: Int,
    val isRecurring: Boolean = false,
    val recurrenceFrequency: String? = null,
    val recurrenceInterval: Int? = null,
    val collaborators: List<UserSummary> = emptyList(),
    val creator: UserSummary? = null,
) {
    val statusEnum: TaskStatus get() = TaskStatus.from(status)
    val priorityEnum: TaskPriority get() = TaskPriority.from(priority)

    /** "Repeats weekly" / "Repeats every 2 weeks" style label, or null when not recurring. */
    val recurrenceLabel: String?
        get() {
            if (!isRecurring || recurrenceFrequency == null) return null
            val unit = when (recurrenceFrequency) {
                "daily" -> "day"
                "weekly" -> "week"
                "monthly" -> "month"
                "yearly" -> "year"
                else -> return null
            }
            val n = recurrenceInterval ?: 1
            return if (n <= 1) "Repeats every $unit" else "Repeats every $n ${unit}s"
        }
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
    val isVideo: Boolean get() = fileType.startsWith("video/") ||
        listOf(".mp4", ".mov", ".webm", ".3gp", ".mkv").any { fileName.endsWith(it, ignoreCase = true) }
    val isSpreadsheet: Boolean get() =
        listOf(".xlsx", ".xls", ".csv").any { fileName.endsWith(it, ignoreCase = true) } ||
            fileType in setOf(
                "text/csv",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
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
