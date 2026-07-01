package com.wmt.app.domain.model

data class DashboardStats(
    val myProjects: Int,
    val activeProjects: Int,
    val myTasks: Int,
    val overdueTasks: Int,
)

data class DashboardData(
    val stats: DashboardStats,
    val recentTasks: List<Task>,
    val myProjects: List<Project>,
)
