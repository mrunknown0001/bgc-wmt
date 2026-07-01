package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DashboardStatsDto(
    @Json(name = "myProjects") val myProjects: Int = 0,
    @Json(name = "activeProjects") val activeProjects: Int = 0,
    @Json(name = "myTasks") val myTasks: Int = 0,
    @Json(name = "overdueTasks") val overdueTasks: Int = 0,
)

@JsonClass(generateAdapter = true)
data class DashboardDto(
    @Json(name = "stats") val stats: DashboardStatsDto = DashboardStatsDto(),
    @Json(name = "myRecentTasks") val recentTasks: List<TaskDto> = emptyList(),
    @Json(name = "myProjects") val myProjects: List<ProjectDto> = emptyList(),
)
