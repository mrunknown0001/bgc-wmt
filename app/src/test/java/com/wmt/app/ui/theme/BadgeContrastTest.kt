package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.wmt.app.domain.model.ApprovalStatus
import com.wmt.app.domain.model.ApprovalStepStatus
import com.wmt.app.domain.model.ProjectStatus
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every status, priority and project badge, measured in both themes.
 *
 * The pill is a 16% tint of its hue, so the ratio that matters is the label
 * against the *composited* tint, not against the hue it came from -- a
 * translucent colour has no contrast ratio until something is behind it. These
 * tests go through the same [badgeColorsOn] the screen uses, so a hue that
 * cannot carry readable text fails here instead of shipping.
 */
class BadgeContrastTest {

    private val aa = 4.5f
    private val surfaces = mapOf("light" to Surface, "dark" to SurfaceDark)

    private fun assertReadable(label: String, hue: Color) {
        for ((theme, surface) in surfaces) {
            val badge = badgeColorsOn(hue, surface)
            val ratio = contrastRatio(badge.content, badge.container)
            assertTrue(
                "%s badge in %s is %.2f:1, below the %.1f:1 AA bar".format(label, theme, ratio, aa),
                ratio >= aa,
            )
            assertEquals("$label container in $theme should be opaque", 1f, badge.container.alpha, 0.001f)
        }
    }

    @Test
    fun `every priority badge is readable`() {
        for (p in TaskPriority.entries) assertReadable("priority ${p.name}", p.statusHue())
    }

    @Test
    fun `every task status badge is readable`() {
        for (s in TaskStatus.entries) assertReadable("task status ${s.name}", s.statusHue())
    }

    @Test
    fun `every project status badge is readable`() {
        for (s in ProjectStatus.entries) assertReadable("project status ${s.name}", s.statusHue())
    }

    @Test
    fun `every approval badge is readable`() {
        for (s in ApprovalStatus.entries) assertReadable("approval ${s.name}", s.statusHue())
        for (s in ApprovalStepStatus.entries) assertReadable("approval step ${s.name}", s.statusHue())
    }

    @Test
    fun `medium priority is blue`() {
        assertEquals(StatusBlue, TaskPriority.MEDIUM.statusHue())
    }

    @Test
    fun `priorities are told apart by hue, not only by label`() {
        val hues = TaskPriority.entries.map { it.statusHue() }
        assertEquals("each priority should have its own hue", hues.size, hues.distinct().size)
    }
}
