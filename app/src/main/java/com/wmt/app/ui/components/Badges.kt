package com.wmt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.ProjectStatus
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.ui.theme.badgeColors

@Composable
private fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun PriorityBadge(priority: TaskPriority, modifier: Modifier = Modifier) {
    val colors = priority.badgeColors()
    Pill(priority.label, colors.container, colors.content, modifier)
}

@Composable
fun TaskStatusChip(status: TaskStatus, modifier: Modifier = Modifier) {
    val colors = status.badgeColors()
    Pill(status.label, colors.container, colors.content, modifier)
}

@Composable
fun ProjectStatusBadge(status: ProjectStatus, modifier: Modifier = Modifier) {
    val colors = status.badgeColors()
    Pill(status.label, colors.container, colors.content, modifier)
}
