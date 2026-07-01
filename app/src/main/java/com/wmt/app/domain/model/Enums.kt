package com.wmt.app.domain.model

/** Canonical task status values; [raw] is the wire value used by the API. */
enum class TaskStatus(val raw: String, val label: String) {
    BACKLOG("backlog", "Backlog"),
    TO_DO("to_do", "To Do"),
    IN_PROGRESS("in_progress", "In Progress"),
    IN_REVIEW("in_review", "In Review"),
    DONE("done", "Done"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun from(raw: String?): TaskStatus =
            entries.firstOrNull { it.raw == raw } ?: BACKLOG
    }
}

enum class TaskPriority(val raw: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    URGENT("urgent", "Urgent");

    companion object {
        fun from(raw: String?): TaskPriority =
            entries.firstOrNull { it.raw == raw } ?: MEDIUM
    }
}

enum class ProjectStatus(val raw: String, val label: String) {
    ACTIVE("active", "Active"),
    ON_HOLD("on_hold", "On Hold"),
    COMPLETED("completed", "Completed"),
    ARCHIVED("archived", "Archived");

    companion object {
        fun from(raw: String?): ProjectStatus =
            entries.firstOrNull { it.raw == raw } ?: ACTIVE
    }
}
