package com.wmt.app.domain.model

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val position: String?,
    val department: Department?,
    val team: Team?,
    val roles: List<String>,
)

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
