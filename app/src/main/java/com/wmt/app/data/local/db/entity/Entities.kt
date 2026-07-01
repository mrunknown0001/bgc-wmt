package com.wmt.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val description: String?,
    val status: String,
    val ownerId: Int?,
    val ownerName: String?,
    val ownerAvatar: String?,
    val dueDate: String?,
    val tasksCount: Int,
    val completedTasksCount: Int,
)

/** Cache of the "My Tasks" list. */
@Entity(tableName = "my_tasks")
data class TaskEntity(
    @PrimaryKey val id: Int,
    val projectId: Int,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val assigneeId: Int?,
    val assigneeName: String?,
    val assigneeAvatar: String?,
    val dueDate: String?,
    val startDate: String?,
    val projectName: String?,
    val subtasksCount: Int,
    val completedSubtasksCount: Int,
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val type: String,
    val taskId: Int?,
    val projectId: Int?,
    val readAt: String?,
    val createdAt: String,
)

/** Single-row cache of the composite dashboard payload, stored as JSON. */
@Entity(tableName = "dashboard")
data class DashboardEntity(
    @PrimaryKey val id: Int = 0,
    val json: String,
)
