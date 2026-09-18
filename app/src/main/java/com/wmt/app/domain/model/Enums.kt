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

/** Status of an approval request. */
enum class ApprovalStatus(val raw: String, val label: String) {
    PENDING("pending", "Pending"),
    CHANGES_REQUESTED("changes_requested", "Changes requested"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    CANCELLED("cancelled", "Cancelled");

    /** The two states that hand a request back to the requestor to act on. */
    val needsRequestorAction: Boolean get() = this == CHANGES_REQUESTED || this == REJECTED

    val isSettled: Boolean get() = this == APPROVED || this == CANCELLED

    companion object {
        fun from(raw: String?): ApprovalStatus =
            entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

/** Status of one attempt at one step of an approval chain. */
enum class ApprovalStepStatus(val raw: String, val label: String) {
    PENDING("pending", "Waiting"),
    ACTIVE("active", "In progress"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    SKIPPED("skipped", "Skipped"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun from(raw: String?): ApprovalStepStatus =
            entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

/** The decision an approver records on a step. The API accepts only these two. */
enum class ApprovalDecisionType(val raw: String, val label: String) {
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected");

    companion object {
        fun from(raw: String?): ApprovalDecisionType =
            entries.firstOrNull { it.raw == raw } ?: APPROVED
    }
}

/**
 * Custom field types an approval project can define. [UNKNOWN] keeps a type the server
 * adds later from breaking the form rather than crashing it.
 */
enum class ApprovalFieldType(val raw: String) {
    TEXT("text"),
    TEXTAREA("textarea"),
    NUMBER("number"),
    DATE("date"),
    WEEK_OF_YEAR("week_of_year"),
    SINGLE_SELECT("single_select"),
    MULTI_SELECT("multi_select"),
    PEOPLE("people"),
    /** Computed server-side; read-only, and never submitted. */
    FORMULA("formula"),
    UNKNOWN("");

    val isSelect: Boolean get() = this == SINGLE_SELECT || this == MULTI_SELECT

    companion object {
        fun from(raw: String?): ApprovalFieldType =
            entries.firstOrNull { it.raw == raw } ?: UNKNOWN
    }
}
