package com.wmt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.wmt.app.domain.model.ApprovalStatus
import com.wmt.app.domain.model.ApprovalStepStatus
import com.wmt.app.domain.model.ProjectStatus
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus

/** A badge colour pair: a tinted [container] with a contrasting [content] colour. */
data class BadgeColors(val container: Color, val content: Color)

/** How strongly a status hue tints the pill behind its label. */
private const val BADGE_TINT_ALPHA = 0.16f

/**
 * Builds the badge pair for [hue] as it will actually appear on [surface].
 *
 * The container is a tint, and a tint has no contrast ratio of its own until
 * you know what is behind it -- so it is composited against the surface first,
 * and the label is then moved in lightness until it clears the bar *against
 * that result*. Which direction it moves falls out of the surface: a pale tint
 * on a light theme needs darker text, a dark tint needs lighter.
 *
 * Kept free of Compose so the contrast test can measure the very same pairs the
 * screen shows, rather than a hand-copied table that drifts from them.
 */
fun badgeColorsOn(hue: Color, surface: Color): BadgeColors {
    val container = hue.copy(alpha = BADGE_TINT_ALPHA).compositeOver(surface)
    val hsl = hue.toHsl()
    val content = if (surface.wcagLuminance() < 0.5f) {
        hsl.lightenUntilReadable(on = container)
    } else {
        hsl.darkenUntilReadable(on = container)
    }
    return BadgeColors(container = container, content = content)
}

@Composable
private fun Color.asBadge(): BadgeColors {
    val surface = MaterialTheme.colorScheme.surface
    return remember(this, surface) { badgeColorsOn(this, surface) }
}

// ---------------------------------------------------------------------------
// Which hue each state wears. Semantic, and the same whatever the accent is:
// "overdue" should not change colour because the organisation picked teal.
// ---------------------------------------------------------------------------

/** The hue a priority is shown in, independent of theme. */
fun TaskPriority.statusHue(): Color = when (this) {
    TaskPriority.URGENT -> StatusRed
    TaskPriority.HIGH -> StatusOrange
    TaskPriority.MEDIUM -> StatusBlue
    TaskPriority.LOW -> StatusGray
}

/** The hue a task status is shown in, independent of theme. */
fun TaskStatus.statusHue(): Color = when (this) {
    TaskStatus.BACKLOG -> StatusGray
    TaskStatus.TO_DO -> StatusBlue
    TaskStatus.IN_PROGRESS -> StatusYellow
    TaskStatus.IN_REVIEW -> StatusPurple
    TaskStatus.DONE -> StatusGreen
    TaskStatus.CANCELLED -> StatusRed
}

/** The hue a project status is shown in, independent of theme. */
fun ProjectStatus.statusHue(): Color = when (this) {
    ProjectStatus.ACTIVE -> StatusGreen
    ProjectStatus.ON_HOLD -> StatusYellow
    ProjectStatus.COMPLETED -> StatusBlue
    ProjectStatus.ARCHIVED -> StatusGray
}

/** The hue an approval status is shown in, independent of theme. */
fun ApprovalStatus.statusHue(): Color = when (this) {
    ApprovalStatus.PENDING -> StatusBlue
    // Amber rather than red: the request is alive and waiting on its author.
    ApprovalStatus.CHANGES_REQUESTED -> StatusAmber
    ApprovalStatus.APPROVED -> StatusGreen
    ApprovalStatus.REJECTED -> StatusRed
    ApprovalStatus.CANCELLED -> StatusGray
}

/** The hue an approval step is shown in, independent of theme. */
fun ApprovalStepStatus.statusHue(): Color = when (this) {
    ApprovalStepStatus.PENDING -> StatusGray
    ApprovalStepStatus.ACTIVE -> StatusYellow
    ApprovalStepStatus.APPROVED -> StatusGreen
    ApprovalStepStatus.REJECTED -> StatusRed
    ApprovalStepStatus.SKIPPED -> StatusGray
    ApprovalStepStatus.CANCELLED -> StatusGray
}

@Composable
fun TaskPriority.badgeColors(): BadgeColors = statusHue().asBadge()

@Composable
fun TaskStatus.badgeColors(): BadgeColors = statusHue().asBadge()

@Composable
fun ProjectStatus.badgeColors(): BadgeColors = statusHue().asBadge()

@Composable
fun ApprovalStatus.badgeColors(): BadgeColors = statusHue().asBadge()

@Composable
fun ApprovalStepStatus.badgeColors(): BadgeColors = statusHue().asBadge()
