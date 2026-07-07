package com.wmt.app.ui.mytasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.ui.components.ConfettiEffect
import com.wmt.app.ui.components.EmptyState
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.OfflineBanner
import com.wmt.app.ui.components.SectionHeader
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.TaskListItem
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.ui.projects.AddTaskDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onTaskClick: (projectId: Int, taskId: Int) -> Unit,
    viewModel: MyTasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-fetch silently whenever this screen comes back into view (e.g. after
    // completing a task on the detail screen) so finished tasks drop out.
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var calendarMode by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        AddTaskDialog(
            creating = state.creating,
            error = state.createError,
            assignableUsers = emptyList(),
            sections = emptyList(),
            onDismiss = {
                showCreate = false
                viewModel.clearCreateError()
            },
            onCreate = { title, status, priority, description, _, _, dueDate,
                         recurrenceFrequency, recurrenceInterval, _ ->
                viewModel.createPersonalTask(
                    title = title,
                    status = status,
                    priority = priority,
                    description = description,
                    dueDate = dueDate,
                    recurrenceFrequency = recurrenceFrequency,
                    recurrenceInterval = recurrenceInterval,
                ) { showCreate = false }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = "My Tasks",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchVisible) "Close search" else "Search tasks",
                        )
                    }
                    IconButton(onClick = { calendarMode = !calendarMode }) {
                        Icon(
                            imageVector = if (calendarMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.CalendarMonth,
                            contentDescription = if (calendarMode) "List view" else "Calendar view",
                            tint = if (calendarMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New personal task")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OfflineBanner(visible = state.offline)

            if (searchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search my tasks") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (!calendarMode) {
                StatsBar(stats = state.stats)
                FilterBar(
                    statusFilter = state.statusFilter,
                    priorityFilter = state.priorityFilter,
                    hasActiveFilters = state.hasActiveFilters,
                    onStatusSelected = viewModel::setStatusFilter,
                    onPrioritySelected = viewModel::setPriorityFilter,
                    onClear = {
                        viewModel.clearFilters()
                        searchVisible = false
                    },
                )
                ThinDivider()
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.allTasks.isEmpty() -> {
                        SkeletonList()
                    }
                    state.error != null && state.allTasks.isEmpty() -> {
                        ErrorView(
                            message = state.error!!,
                            onRetry = viewModel::refresh,
                        )
                    }
                    calendarMode -> {
                        TaskCalendar(
                            // Server feed includes collaborator tasks; fall back to
                            // assigned tasks until (or if) it hasn't loaded.
                            tasks = state.calendarTasks ?: state.allTasks,
                            onTaskClick = onTaskClick,
                            onToggleComplete = viewModel::toggleComplete,
                            onMonthChange = viewModel::loadCalendarMonth,
                        )
                    }
                    state.groups.isEmpty() -> {
                        if (state.hasActiveFilters) {
                            EmptyState(
                                title = "No matching tasks",
                                message = "Try changing or clearing the filters.",
                            )
                        } else {
                            EmptyState(
                                title = "You're all caught up",
                                message = "No tasks assigned to you right now.",
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            state.groups.forEach { group ->
                                val expanded = expandedStates[group.title] ?: true
                                item(key = "header-${group.title}") {
                                    SectionHeader(
                                        title = group.title,
                                        count = group.tasks.size,
                                        expanded = expanded,
                                        onToggle = { expandedStates[group.title] = !expanded },
                                    )
                                }
                                if (expanded) {
                                    items(group.tasks, key = { it.id }) { task ->
                                        TaskListItem(
                                            task = task,
                                            onClick = { onTaskClick(task.projectId, task.id) },
                                            onToggleComplete = { viewModel.toggleComplete(task) },
                                        )
                                        ThinDivider(startIndent = 52.dp)
                                    }
                                }
                            }
                        }
                    }
                }
                ConfettiEffect(burstKey = state.celebrations, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Compact "open · overdue · due today" strip mirroring the web app's stats bar. */
@Composable
private fun StatsBar(stats: MyTasksStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        StatItem(value = stats.open, label = "Open", emphasize = false)
        StatItem(value = stats.overdue, label = "Overdue", emphasize = stats.overdue > 0)
        StatItem(value = stats.dueToday, label = "Due today", emphasize = false)
    }
}

@Composable
private fun StatItem(value: Int, label: String, emphasize: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterBar(
    statusFilter: TaskStatus?,
    priorityFilter: TaskPriority?,
    hasActiveFilters: Boolean,
    onStatusSelected: (TaskStatus?) -> Unit,
    onPrioritySelected: (TaskPriority?) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownFilterChip(
            label = statusFilter?.label ?: "Status",
            active = statusFilter != null,
            options = listOf<TaskStatus?>(null) + TaskStatus.entries,
            optionLabel = { it?.label ?: "All statuses" },
            onSelected = onStatusSelected,
        )
        DropdownFilterChip(
            label = priorityFilter?.label ?: "Priority",
            active = priorityFilter != null,
            options = listOf<TaskPriority?>(null) + TaskPriority.entries,
            optionLabel = { it?.label ?: "All priorities" },
            onSelected = onPrioritySelected,
        )
        if (hasActiveFilters) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownFilterChip(
    label: String,
    active: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}
