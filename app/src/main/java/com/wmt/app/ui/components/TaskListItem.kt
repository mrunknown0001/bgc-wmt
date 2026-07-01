package com.wmt.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.ui.theme.badgeColors
import com.wmt.app.util.DateUtils

// Deterministic soft colors for project accent dots.
private val projectDotColors = listOf(
    Color(0xFFF2685F), Color(0xFF5A5AD6), Color(0xFF00838F), Color(0xFF2E7D32),
    Color(0xFFEF6C00), Color(0xFF6A1B9A), Color(0xFFC2185B), Color(0xFF00695C),
)

fun projectDotColor(projectId: Int): Color =
    projectDotColors[projectId.mod(projectDotColors.size)]

/**
 * A tappable circular completion toggle: an outlined ring when open, a filled
 * primary disc with a check when done. Pass [onToggle] to make it interactive.
 */
@Composable
fun CompletionCircle(
    done: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    enabled: Boolean = true,
    onToggle: (() -> Unit)? = null,
) {
    val ring = MaterialTheme.colorScheme.outline
    val clickModifier = if (onToggle != null && enabled) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = size),
            role = Role.Checkbox,
            onClick = onToggle,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(size)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.62f),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.5.dp, ring, CircleShape),
            )
        }
    }
}

/**
 * Signature flat task row: [completion circle] · title · meta line · assignee.
 * Reused across My Tasks, project detail and dashboard so tasks read the same
 * everywhere. Rows are separated by [ThinDivider], not cards.
 */
@Composable
fun TaskListItem(
    task: Task,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleComplete: (() -> Unit)? = null,
    showProject: Boolean = true,
) {
    val done = task.statusEnum == TaskStatus.DONE
    val overdue = !done && (DateUtils.daysUntil(task.dueDate)?.let { it < 0 } == true)
    val due = DateUtils.formatDate(task.dueDate)
    val priority = task.priorityEnum
    val showPriority = priority == TaskPriority.HIGH || priority == TaskPriority.URGENT
    val hasMeta = (showProject && task.project != null) || due != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompletionCircle(done = done, onToggle = onToggleComplete)
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasMeta) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showProject) {
                        task.project?.name?.let { name ->
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(projectDotColor(task.projectId)),
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (due != null) {
                        if (showProject && task.project != null) {
                            MetaDot()
                        }
                        Icon(
                            Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = due,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        if (showPriority) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = priority.label,
                tint = priority.badgeColors().content,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
        if (task.assignee != null) {
            Spacer(Modifier.width(8.dp))
            UserAvatar(task.assignee, size = 26.dp)
        }
    }
}

@Composable
private fun MetaDot() {
    Box(
        Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
    )
}

/** Hairline divider used between flat list rows. */
@Composable
fun ThinDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .size(width = 0.dp, height = 1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * Collapsible section header with a rotating chevron, title and count — matches
 * the grouped-list rhythm used across the app.
 */
@Composable
fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "chevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}
