package com.wmt.app.ui.mytasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.TaskListItem
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.theme.badgeColors
import com.wmt.app.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Month calendar of the user's tasks (web app's Calendar feature, phone-sized):
 * a Sunday-start grid with priority-coloured dots per day, and the selected
 * day's tasks listed underneath.
 */
@Composable
fun TaskCalendar(
    tasks: List<Task>,
    onTaskClick: (projectId: Int, taskId: Int) -> Unit,
    onToggleComplete: (Task) -> Unit,
    modifier: Modifier = Modifier,
    onMonthChange: (String) -> Unit = {},
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    // Let the caller fetch the server's calendar feed for the visible month.
    LaunchedEffect(month) {
        onMonthChange("%04d-%02d".format(month.year, month.monthValue))
    }

    val tasksByDate = remember(tasks) {
        tasks.mapNotNull { task ->
            DateUtils.parseDate(task.dueDate)?.let { it to task }
        }.groupBy({ it.first }, { it.second })
    }
    val selectedTasks = tasksByDate[selected].orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "month-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
                TextButton(onClick = {
                    month = YearMonth.now()
                    selected = LocalDate.now()
                }) { Text("Today") }
            }
        }

        item(key = "grid") {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Sunday-start grid with leading/trailing overflow days, like the web calendar.
                val firstOfMonth = month.atDay(1)
                val gridStart = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value % 7).toLong())
                val weeks = generateSequence(gridStart) { it.plusWeeks(1) }
                    .takeWhile { it <= month.atEndOfMonth() }
                    .toList()
                weeks.forEach { weekStart ->
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { offset ->
                            val date = weekStart.plusDays(offset.toLong())
                            DayCell(
                                date = date,
                                inMonth = YearMonth.from(date) == month,
                                selected = date == selected,
                                tasks = tasksByDate[date].orEmpty(),
                                onClick = { selected = date },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        item(key = "selected-header") {
            Text(
                text = selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        if (selectedTasks.isEmpty()) {
            item(key = "selected-empty") {
                EmptyState(
                    title = "Nothing due",
                    message = "No tasks due on this day.",
                )
            }
        } else {
            items(selectedTasks, key = { "cal-task-${it.id}" }) { task ->
                TaskListItem(
                    task = task,
                    onClick = { onTaskClick(task.projectId, task.id) },
                    onToggleComplete = { onToggleComplete(task) },
                )
                ThinDivider(startIndent = 52.dp)
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    tasks: List<Task>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = date == LocalDate.now()
    Column(
        modifier = modifier
            .aspectRatio(0.9f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                },
            )
            .then(
                if (isToday && !selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.size(4.dp))
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.size(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tasks.take(3).forEach { task ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(priorityDotColor(task.priorityEnum)),
                )
            }
        }
        if (tasks.size > 3) {
            Text(
                text = "+${tasks.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun priorityDotColor(priority: TaskPriority) = priority.badgeColors().content
