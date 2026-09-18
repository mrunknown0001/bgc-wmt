package com.wmt.app.domain.model

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val position: String?,
    val department: Department?,
    val team: Team?,
    val roles: List<String>,
    val isActive: Boolean = true,
    val capabilities: UserCapabilities = UserCapabilities(),
) {
    /** For the avatar component and anywhere else a compact reference is expected. */
    val summary: UserSummary get() = UserSummary(id = id, name = name)
}

/**
 * What this person may do, as the server decided it. Defaults are all-off so a user
 * cached before the app read capabilities hides features rather than offering ones the
 * API would refuse.
 */
data class UserCapabilities(
    val canApprove: Boolean = false,
    val canRequest: Boolean = false,
    val canExpandUploads: Boolean = false,
    val canCreateProject: Boolean = false,
    val canManageTasks: Boolean = false,
    val canManageProjects: Boolean = false,
    /** Wider than [canApprove]: admins and executives reach approvals without it. */
    val canAccessApprovals: Boolean = false,
) {
    /**
     * Whether the Approvals section should appear at all. An approver sees the queue; a
     * requestor sees only their own submissions, but both need the entry point.
     */
    val canUseApprovals: Boolean get() = canAccessApprovals || canRequest
}

data class Department(val id: Int, val name: String)

data class Team(val id: Int, val name: String)

/** Lightweight user reference embedded in projects/tasks/comments. */
data class UserSummary(
    val id: Int,
    val name: String,
    val avatarUrl: String? = null,
) {
    val initials: String
        get() = name.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}
