package com.wmt.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.DashboardStats
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.Task
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.OfflineBanner
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.TaskListItem
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.ui.components.projectDotColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onTaskClick: (projectId: Int, taskId: Int) -> Unit,
    onProjectClick: (projectId: Int) -> Unit,
    onOpenMyTasks: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    unreadCount: Int = 0,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = "Home",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenInbox) {
                        if (unreadCount > 0) {
                            BadgedBox(badge = { Badge { Text(unreadCount.coerceAtMost(99).toString()) } }) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        } else {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OfflineBanner(visible = state.offline)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.data == null -> SkeletonList()
                    state.error != null && state.data == null -> ErrorView(
                        message = state.error!!,
                        onRetry = viewModel::refresh,
                    )
                    state.data != null -> DashboardContent(
                        stats = state.data!!.stats,
                        recentTasks = state.data!!.recentTasks,
                        projects = state.data!!.myProjects,
                        onTaskClick = onTaskClick,
                        onProjectClick = onProjectClick,
                        onOpenMyTasks = onOpenMyTasks,
                        onOpenProjects = onOpenProjects,
                    )
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun DashboardContent(
    stats: DashboardStats,
    recentTasks: List<Task>,
    projects: List<Project>,
    onTaskClick: (projectId: Int, taskId: Int) -> Unit,
    onProjectClick: (projectId: Int) -> Unit,
    onOpenMyTasks: () -> Unit,
    onOpenProjects: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Greeting hero
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = greeting(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Stat grid (2x2)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("My tasks", stats.myTasks, MaterialTheme.colorScheme.primary, onOpenMyTasks, Modifier.weight(1f))
                StatCard(
                    "Overdue",
                    stats.overdueTasks,
                    if (stats.overdueTasks > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    onOpenMyTasks,
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("My projects", stats.myProjects, MaterialTheme.colorScheme.tertiary, onOpenProjects, Modifier.weight(1f))
                StatCard("Active", stats.activeProjects, MaterialTheme.colorScheme.secondary, onOpenProjects, Modifier.weight(1f))
            }
        }

        // My tasks
        SectionTitle(title = "My tasks", actionLabel = "See all", onAction = onOpenMyTasks)
        if (recentTasks.isEmpty()) {
            EmptyLine("No recent tasks.")
        } else {
            GroupedCard {
                recentTasks.forEachIndexed { index, task ->
                    TaskListItem(
                        task = task,
                        onClick = { onTaskClick(task.projectId, task.id) },
                    )
                    if (index != recentTasks.lastIndex) ThinDivider(startIndent = 52.dp)
                }
            }
        }

        // Projects
        SectionTitle(title = "Projects", actionLabel = "See all", onAction = onOpenProjects)
        if (projects.isEmpty()) {
            EmptyLine("No projects yet.")
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(project = project, onClick = { onProjectClick(project.id) })
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Int,
    valueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        content()
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    val accent = projectDotColor(project.id)
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    project.name.trim().take(1).uppercase().ifBlank { "#" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
        Text(
            project.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { project.progress },
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Text(
                "  ${project.completedTasksCount}/${project.tasksCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
