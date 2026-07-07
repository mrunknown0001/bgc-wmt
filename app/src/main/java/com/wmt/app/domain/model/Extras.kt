package com.wmt.app.domain.model

/** One entry in the user's lightweight personal checklist (separate from tasks). */
data class PersonalTodo(
    val id: Int,
    val title: String,
    val isCompleted: Boolean,
    val position: Int,
)

/** A project hit from global search (compact — not a full [Project]). */
data class SearchProjectHit(
    val id: Int,
    val name: String,
    val status: String,
) {
    val statusEnum: ProjectStatus get() = ProjectStatus.from(status)
}

data class SearchResults(
    val projects: List<SearchProjectHit> = emptyList(),
    val tasks: List<Task> = emptyList(),
) {
    val isEmpty: Boolean get() = projects.isEmpty() && tasks.isEmpty()
}
