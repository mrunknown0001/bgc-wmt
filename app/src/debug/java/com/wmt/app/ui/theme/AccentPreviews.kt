package com.wmt.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.ApprovalStatus
import com.wmt.app.domain.model.ProjectStatus
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.ui.components.ApprovalStatusBadge
import com.wmt.app.ui.components.CompletionCircle
import com.wmt.app.ui.components.PriorityBadge
import com.wmt.app.ui.components.ProjectStatusBadge
import com.wmt.app.ui.components.TaskStatusChip

// ---------------------------------------------------------------------------
// One preview per accent, each showing light over dark.
//
// Debug source set on purpose: these are a way to look at all eight palettes
// without eight trips to web Settings, and they have no business being
// compiled into a release. The contrast tests prove the ratios; this is for
// the half of "looks right" that a ratio cannot tell you -- whether the dark
// end of Amber still reads as amber, whether two badges sit well side by side.
// ---------------------------------------------------------------------------

@Composable
private fun Swatch(label: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(label, color = content, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AccentSheet(accent: Accent, dark: Boolean) {
    WmtTheme(darkTheme = dark, accent = accent) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val scheme = MaterialTheme.colorScheme
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${accent.key} · ${if (dark) "dark" else "light"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onBackground,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Swatch("primary", scheme.primary, scheme.onPrimary)
                    Swatch("container", scheme.primaryContainer, scheme.onPrimaryContainer)
                    Swatch("secondary", scheme.secondary, scheme.onSecondary)
                    Swatch("tertiary", scheme.tertiary, scheme.onTertiary)
                }

                // Surfaces, so a neutral in the wrong slot is visible as a step
                // that goes the wrong way.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        scheme.surfaceContainerLowest,
                        scheme.surfaceContainerLow,
                        scheme.surfaceContainer,
                        scheme.surfaceContainerHigh,
                        scheme.surfaceContainerHighest,
                    ).forEach {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(it),
                        )
                    }
                    Swatch("error", scheme.errorContainer, scheme.onErrorContainer)
                }

                // The badges, which keep their own hues whatever the accent is.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompletionCircle(done = false)
                    CompletionCircle(done = true)
                    PriorityBadge(TaskPriority.URGENT)
                    PriorityBadge(TaskPriority.MEDIUM)
                    PriorityBadge(TaskPriority.LOW)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TaskStatusChip(TaskStatus.IN_PROGRESS)
                    TaskStatusChip(TaskStatus.IN_REVIEW)
                    TaskStatusChip(TaskStatus.DONE)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ProjectStatusBadge(ProjectStatus.ON_HOLD)
                    ApprovalStatusBadge(ApprovalStatus.CHANGES_REQUESTED)
                    ApprovalStatusBadge(ApprovalStatus.REJECTED)
                }

                // Identity hues: fixed, and deliberately unmoved by the accent.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProjectDotHues.forEach {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AvatarHues.forEach {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(it),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("W", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BothThemes(accent: Accent) {
    Column {
        AccentSheet(accent, dark = false)
        AccentSheet(accent, dark = true)
    }
}

@Preview(name = "Blue (default)", widthDp = 400)
@Composable
private fun PreviewBlue() = BothThemes(Accent.Blue)

@Preview(name = "Green", widthDp = 400)
@Composable
private fun PreviewGreen() = BothThemes(Accent.Green)

@Preview(name = "Amber", widthDp = 400)
@Composable
private fun PreviewAmber() = BothThemes(Accent.Amber)

@Preview(name = "Red", widthDp = 400)
@Composable
private fun PreviewRed() = BothThemes(Accent.Red)

@Preview(name = "Teal", widthDp = 400)
@Composable
private fun PreviewTeal() = BothThemes(Accent.Teal)

@Preview(name = "Indigo", widthDp = 400)
@Composable
private fun PreviewIndigo() = BothThemes(Accent.Indigo)

@Preview(name = "Orange", widthDp = 400)
@Composable
private fun PreviewOrange() = BothThemes(Accent.Orange)

@Preview(name = "Purple", widthDp = 400)
@Composable
private fun PreviewPurple() = BothThemes(Accent.Purple)
