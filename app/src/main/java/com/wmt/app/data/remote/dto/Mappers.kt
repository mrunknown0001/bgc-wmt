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
import com.wmt.app.domain.model.PersonalTodo
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.SearchProjectHit
import com.wmt.app.domain.model.ProjectDetail
import com.wmt.app.domain.model.ProjectSection
import com.wmt.app.domain.model.ProjectSummary
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.model.PausePreview
import com.wmt.app.domain.model.TaskClock
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.model.Team
import com.wmt.app.domain.model.User
import com.wmt.app.domain.model.UserCapabilities
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
    // Absent from a user cached before the app read these; treat the account as active
    // and every capability as off rather than inventing permissions.
    isActive = isActive ?: true,
    capabilities = capabilities?.toDomain() ?: UserCapabilities(),
)

fun UserCapabilitiesDto.toDomain() = UserCapabilities(
    canApprove = canApprove,
    canRequest = canRequest,
    canExpandUploads = canExpandUploads,
    canCreateProject = canCreateProject,
    canManageTasks = canManageTasks,
    canManageProjects = canManageProjects,
    canAccessApprovals = canAccessApprovals,
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
    projectId = projectId ?: project?.id ?: 0,
    title = title,
    description = description,
    status = status,
    priority = priority,
    assignee = assignee?.toDomain(),
    dueDate = dueDate,
    startDate = startDate,
    completedAt = completedAt,
    project = project?.toDomain(),
    subtasksCount = subtasksCount,
    completedSubtasksCount = completedSubtasksCount,
    isRecurring = isRecurring,
    recurrenceFrequency = recurrenceFrequency,
    recurrenceInterval = recurrenceInterval,
    collaborators = collaborators.map { it.toDomain() },
    creator = creator?.toDomain(),
)

fun ProjectDetailResponse.toDomain(): ProjectDetail {
    val allTasks = tasks.map { it.toDomain() }
    // The show endpoint doesn't withCount() the project (unlike the index/dashboard),
    // so its counts arrive as 0 — derive the progress counter from the task payload.
    val domainProject = project.toDomain().let { p ->
        if (p.tasksCount == 0 && allTasks.isNotEmpty()) {
            p.copy(
                tasksCount = allTasks.size,
                completedTasksCount = allTasks.count { it.statusEnum == TaskStatus.DONE },
            )
        } else {
            p
        }
    }
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
    clock = toClock(),
)

/**
 * The clock, assembled from the two halves the payload carries: the motion columns on
 * the task itself, and the figures and switches the endpoint computes alongside it.
 */
private fun TaskDetailResponse.toClock() = TaskClock(
    startedAt = task.startedAt,
    pausedAt = task.motionPausedAt,
    resumedAt = task.motionResumedAt,
    timeInMotionMinutes = timeInMotionMinutes,
    loggedMinutes = loggedMinutes,
    pausedMinutes = pausedMinutes ?: task.motionPausedMinutes,
    isVisible = showTimeInMotion,
    isProjectClosed = projectIsClosed,
    // Matches the server: a finished task refuses a start, and cancelled counts even
    // though only done is stamped with a completion time.
    isTaskFinished = task.completedAt != null ||
        task.status == TaskStatus.DONE.raw ||
        task.status == TaskStatus.CANCELLED.raw,
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
        bookmarkedAt = bookmarkedAt,
        archivedAt = archivedAt,
    )
}

fun SearchProjectDto.toDomain() = SearchProjectHit(id = id, name = name, status = status)

/** Compact search/calendar task hit → domain Task (list rendering + navigation only). */
fun SearchTaskDto.toDomain() = Task(
    id = id,
    projectId = projectId ?: 0,
    title = title,
    description = null,
    status = status,
    priority = priority,
    assignee = null,
    dueDate = dueDate,
    startDate = null,
    project = projectName?.let { ProjectSummary(id = projectId ?: 0, name = it) },
    subtasksCount = 0,
    completedSubtasksCount = 0,
)

fun PersonalTodoDto.toDomain() = PersonalTodo(
    id = id,
    title = title,
    isCompleted = isCompleted ?: false,
    position = position ?: 0,
)

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

/**
 * A clock response onto the existing clock.
 *
 * Start answers with only the fields it touched, so the previous state is carried
 * forward rather than blanked: pausing after a start must not lose the project switch
 * that decided the strip was shown in the first place.
 */
fun TaskClockDto.toDomain(previous: TaskClock): TaskClock = previous.copy(
    startedAt = startedAt,
    pausedAt = motionPausedAt,
    resumedAt = motionResumedAt,
    timeInMotionMinutes = timeInMotionMinutes ?: previous.timeInMotionMinutes,
    pausedMinutes = motionPausedMinutes ?: previous.pausedMinutes,
    // Only a pause reports minutes, and they add to the day's running total.
    loggedMinutes = loggedMinutes?.let { (previous.loggedMinutes ?: 0) + it }
        ?: previous.loggedMinutes,
)

fun PausePreviewDto.toDomain() = PausePreview(
    suggestedMinutes = suggestedMinutes,
    from = from,
    alreadyLoggedToday = alreadyLoggedToday,
    creditedTo = creditedTo,
)
