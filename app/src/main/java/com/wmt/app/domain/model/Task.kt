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
    /** The full replaced text of a free-text answer can be fetched for this row. */
    val hasPreviousValue: Boolean = false,
)

/** The full text a custom field change replaced, fetched on demand. */
data class PreviousValue(
    val field: String?,
    val value: String?,
    val changedAt: String?,
)

/** A dependency or dependent, named and with its status, so it can be opened. */
data class TaskLink(
    val id: Int,
    val title: String,
    val status: String,
) {
    val statusEnum: TaskStatus get() = TaskStatus.from(status)
    val isOpen: Boolean get() = statusEnum != TaskStatus.DONE && statusEnum != TaskStatus.CANCELLED
}

data class Tag(val id: Int, val name: String)

/**
 * What the server says this person may do to this task, before they try.
 *
 * Every field is the server's own answer, never worked out here: a rule restated in the
 * app is a second copy, and the two drift. The screen reads these to draw controls as
 * enabled, disabled or replaced by a notice saying why.
 */
data class TaskRules(
    val canEdit: Boolean = true,
    val canDelete: Boolean = true,
    val canEditCustomFields: Boolean = false,
    val canChangeAssignmentAndDates: Boolean = true,
    /** Closing would be refused until a comment carries an attachment. */
    val closeNeedsAttachment: Boolean = false,
    /** Why this task cannot have subtasks, in the server's words; null when it can. */
    val subtaskRestriction: String? = null,
    /** An open dependency stands in the way of marking it done. */
    val isBlocked: Boolean = false,
    val projectIsClosed: Boolean = false,
)

/**
 * One custom field on a task: its definition, this task's value, and whether the viewer
 * is held back by a lock. The definition reuses [ApprovalCustomField] because the server
 * stores both kinds identically, which lets one set of editors serve both screens.
 */
data class TaskCustomField(
    val field: ApprovalCustomField,
    val value: ApprovalFieldValue?,
    val isLocked: Boolean = false,
    /** Locked, and this person is not one of the project's managers. */
    val lockedForMe: Boolean = false,
) {
    // `this.` because inside a getter a bare `field` means the property's backing field.
    val id: Int get() = this.field.id

    /** Something this screen can offer an editor for; formula and people are web-only. */
    val isEditableType: Boolean
        get() = this.field.typeEnum !in setOf(
            ApprovalFieldType.FORMULA,
            ApprovalFieldType.PEOPLE,
            ApprovalFieldType.UNKNOWN,
        )
}

/** Aggregate task view backing the Task Detail screen. */
data class TaskDetail(
    val task: Task,
    val comments: List<Comment>,
    val activities: List<Activity>,
    /** Users who can be @mentioned in comments (project members + owner). */
    val members: List<UserSummary> = emptyList(),
    val subtasks: List<Task> = emptyList(),
    /** The task clock, and whether this project shows one at all. */
    val clock: TaskClock = TaskClock(),
    val rules: TaskRules = TaskRules(),
    val dependencies: List<TaskLink> = emptyList(),
    val dependents: List<TaskLink> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val customFields: List<TaskCustomField> = emptyList(),
    /** How many activity rows exist in all, so "load more" is offered only when there are. */
    val totalActivities: Int? = null,
)
