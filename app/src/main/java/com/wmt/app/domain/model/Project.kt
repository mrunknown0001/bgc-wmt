package com.wmt.app.domain.model

data class ProjectSummary(
    val id: Int,
    val name: String,
)

data class Project(
    val id: Int,
    val name: String,
    val description: String?,
    val status: String,
    val owner: UserSummary?,
    val dueDate: String?,
    val tasksCount: Int,
    val completedTasksCount: Int,
) {
    val statusEnum: ProjectStatus get() = ProjectStatus.from(status)

    val progress: Float
        get() = if (tasksCount <= 0) 0f else completedTasksCount.toFloat() / tasksCount
}

/** A grouping of tasks within a project (e.g. a board column / phase). */
data class ProjectSection(
    val id: Int,
    val name: String,
    val tasks: List<Task>,
)

/** Full project view returned by GET /api/projects/{id}. */
data class ProjectDetail(
    val project: Project,
    val sections: List<ProjectSection>,
    val tasks: List<Task>,
    val members: List<UserSummary> = emptyList(),
) {
    /** True when the project organises tasks into explicit sections. */
    val hasSections: Boolean get() = sections.isNotEmpty()

    /** Everyone who can be assigned a task: the owner plus members, de-duplicated. */
    val assignableUsers: List<UserSummary>
        get() = (listOfNotNull(project.owner) + members).distinctBy { it.id }
}
