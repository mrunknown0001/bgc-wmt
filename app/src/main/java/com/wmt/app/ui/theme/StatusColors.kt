package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.wmt.app.domain.model.ProjectStatus
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus

/** A badge colour pair: a tinted [container] with a contrasting [content] colour. */
data class BadgeColors(val container: Color, val content: Color)

private fun Color.asBadge(): BadgeColors = BadgeColors(copy(alpha = 0.16f), this)

fun TaskPriority.badgeColors(): BadgeColors = when (this) {
    TaskPriority.URGENT -> StatusRed
    TaskPriority.HIGH -> StatusOrange
    TaskPriority.MEDIUM -> StatusAmber
    TaskPriority.LOW -> StatusGray
}.asBadge()

fun TaskStatus.badgeColors(): BadgeColors = when (this) {
    TaskStatus.BACKLOG -> StatusGray
    TaskStatus.TO_DO -> StatusBlue
    TaskStatus.IN_PROGRESS -> StatusYellow
    TaskStatus.IN_REVIEW -> StatusPurple
    TaskStatus.DONE -> StatusGreen
    TaskStatus.CANCELLED -> StatusRed
}.asBadge()

fun ProjectStatus.badgeColors(): BadgeColors = when (this) {
    ProjectStatus.ACTIVE -> StatusGreen
    ProjectStatus.ON_HOLD -> StatusYellow
    ProjectStatus.COMPLETED -> StatusBlue
    ProjectStatus.ARCHIVED -> StatusGray
}.asBadge()
