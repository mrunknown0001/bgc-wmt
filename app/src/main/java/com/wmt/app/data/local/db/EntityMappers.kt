package com.wmt.app.data.local.db

import com.wmt.app.data.local.db.entity.NotificationEntity
import com.wmt.app.data.local.db.entity.ProjectEntity
import com.wmt.app.data.local.db.entity.TaskEntity
import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.model.NotificationData
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.ProjectSummary
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.UserSummary

fun Project.toEntity() = ProjectEntity(
    id = id,
    name = name,
    description = description,
    status = status,
    ownerId = owner?.id,
    ownerName = owner?.name,
    ownerAvatar = owner?.avatarUrl,
    dueDate = dueDate,
    tasksCount = tasksCount,
    completedTasksCount = completedTasksCount,
)

fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    description = description,
    status = status,
    owner = ownerId?.let { UserSummary(it, ownerName.orEmpty(), ownerAvatar) },
    dueDate = dueDate,
    tasksCount = tasksCount,
    completedTasksCount = completedTasksCount,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    projectId = projectId,
    title = title,
    description = description,
    status = status,
    priority = priority,
    assigneeId = assignee?.id,
    assigneeName = assignee?.name,
    assigneeAvatar = assignee?.avatarUrl,
    dueDate = dueDate,
    startDate = startDate,
    projectName = project?.name,
    subtasksCount = subtasksCount,
    completedSubtasksCount = completedSubtasksCount,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    projectId = projectId,
    title = title,
    description = description,
    status = status,
    priority = priority,
    assignee = assigneeId?.let { UserSummary(it, assigneeName.orEmpty(), assigneeAvatar) },
    dueDate = dueDate,
    startDate = startDate,
    project = projectName?.let { ProjectSummary(projectId, it) },
    subtasksCount = subtasksCount,
    completedSubtasksCount = completedSubtasksCount,
)

fun Notification.toEntity() = NotificationEntity(
    id = id,
    title = data.title,
    body = data.body,
    type = data.type,
    taskId = data.taskId,
    projectId = data.projectId,
    approvalProjectId = data.approvalProjectId,
    approvalRequestId = data.approvalRequestId,
    readAt = readAt,
    createdAt = createdAt,
)

fun NotificationEntity.toDomain() = Notification(
    id = id,
    data = NotificationData(
        title = title,
        body = body,
        type = type,
        taskId = taskId,
        projectId = projectId,
        approvalProjectId = approvalProjectId,
        approvalRequestId = approvalRequestId,
    ),
    readAt = readAt,
    createdAt = createdAt,
)
