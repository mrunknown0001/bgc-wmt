package com.wmt.app.data.remote.dto

import androidx.core.text.HtmlCompat
import com.wmt.app.domain.model.Activity
import com.wmt.app.domain.model.Attachment
import com.wmt.app.domain.model.Comment
import com.wmt.app.domain.model.DashboardData
import com.wmt.app.domain.model.DashboardStats
import com.wmt.app.domain.model.Department
import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.model.NotificationData
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.ProjectDetail
import com.wmt.app.domain.model.ProjectSection
import com.wmt.app.domain.model.ProjectSummary
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.model.Team
import com.wmt.app.domain.model.User
import com.wmt.app.domain.model.UserSummary

fun UserSummaryDto.toDomain() = UserSummary(id = id, name = name, avatarUrl = avatarUrl)

fun ProjectSummaryDto.toDomain() = ProjectSummary(id = id, name = name)

fun UserDto.toDomain() = User(
    id = id,
    name = name,
    email = email,
    position = position,
    department = department?.let { Department(it.id, it.name) },
    team = team?.let { Team(it.id, it.name) },
    roles = roles,
)

fun ProjectDto.toDomain() = Project(
    id = id,
    name = name,
    description = description,
    status = status,
    owner = owner?.toDomain(),
    dueDate = dueDate,
    tasksCount = tasksCount,
    completedTasksCount = completedTasksCount,
)

fun TaskDto.toDomain() = Task(
    id = id,
    projectId = projectId,
    title = title,
    description = description,
    status = status,
    priority = priority,
    assignee = assignee?.toDomain(),
    dueDate = dueDate,
    startDate = startDate,
    project = project?.toDomain(),
    subtasksCount = subtasksCount,
    completedSubtasksCount = completedSubtasksCount,
)

fun ProjectDetailResponse.toDomain(): ProjectDetail {
    val domainProject = project.toDomain()
    val allTasks = tasks.map { it.toDomain() }
    val domainSections = if (sections.isNotEmpty()) {
        val tasksBySection = tasks.groupBy { it.sectionId }
        val mapped = sections.map { s ->
            ProjectSection(id = s.id, name = s.name, tasks = tasksBySection[s.id].orEmpty().map { it.toDomain() })
        }
        // Tasks that don't belong to any section would otherwise be hidden — surface them.
        val orphaned = tasks.filter { t -> sections.none { it.id == t.sectionId } }
        if (orphaned.isNotEmpty()) {
            mapped + ProjectSection(id = -1, name = "Other", tasks = orphaned.map { it.toDomain() })
        } else {
            mapped
        }
    } else {
        emptyList()
    }
    return ProjectDetail(
        project = domainProject,
        sections = domainSections,
        tasks = allTasks,
        members = project.members.map { it.toDomain() },
    )
}

fun CommentDto.toDomain() = Comment(
    id = id,
    body = body,
    author = user?.toDomain(),
    createdAt = createdAt,
    attachments = attachments.map { it.toDomain() },
)

fun AttachmentDto.toDomain() = Attachment(
    id = id,
    fileName = fileName,
    fileType = fileType,
    fileSizeBytes = fileSize,
    url = url,
)

fun ActivityDto.toDomain() = Activity(
    id = id,
    description = description?.takeIf { it.isNotBlank() } ?: activityText(field, oldValue, newValue),
    actor = user?.toDomain(),
    createdAt = createdAt,
)

/** Builds a human-readable line for activity entries that only carry a field change. */
private fun activityText(field: String?, old: String?, new: String?): String = when {
    field != null && (old != null || new != null) ->
        "changed $field from ${old ?: "—"} to ${new ?: "—"}"
    field != null -> "updated $field"
    else -> "updated the task"
}

fun TaskDetailResponse.toDomain() = TaskDetail(
    task = task.toDomain(),
    comments = comments.map { it.toDomain() },
    activities = activities.map { it.toDomain() },
    members = members.map { it.toDomain() },
    subtasks = subtasks.map { it.toDomain() },
)

fun NotificationDto.toDomain(): Notification {
    val d = data
    val title = d.title ?: when (d.type) {
        "task_comment_mention" -> "${d.mentionedBy ?: "Someone"} mentioned you"
        "task_comment" -> "New comment"
        "task_assigned" -> "${d.assignedBy ?: "Someone"} assigned you a task"
        else -> d.type.replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Notification" }
    }
    val bodySource = d.body ?: d.commentPreview ?: d.taskTitle.orEmpty()
    val body = stripHtml(bodySource).ifBlank {
        listOfNotNull(d.taskTitle, d.projectName).joinToString(" • ")
    }
    return Notification(
        id = id,
        data = NotificationData(
            title = title,
            body = body,
            type = d.type,
            taskId = d.taskId,
            projectId = d.projectId,
        ),
        readAt = readAt,
        createdAt = createdAt,
    )
}

/** Flattens an HTML snippet to plain text for compact list display (e.g. notifications). */
private fun stripHtml(html: String): String =
    if (html.isBlank()) "" else HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

fun DashboardDto.toDomain() = DashboardData(
    stats = DashboardStats(
        myProjects = stats.myProjects,
        activeProjects = stats.activeProjects,
        myTasks = stats.myTasks,
        overdueTasks = stats.overdueTasks,
    ),
    recentTasks = recentTasks.map { it.toDomain() },
    myProjects = myProjects.map { it.toDomain() },
)
