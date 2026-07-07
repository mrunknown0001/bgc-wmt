package com.wmt.app.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.Project
import com.wmt.app.ui.components.ConfettiEffect
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.HtmlText
import com.wmt.app.ui.components.ProjectStatusBadge
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.TaskListItem
import com.wmt.app.ui.components.ThinDivider
import com.wmt.app.ui.components.UserAvatar
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.ui.components.projectDotColor
import com.wmt.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    onTaskClick: (projectId: Int, taskId: Int) -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-fetch silently whenever this screen comes back into view (e.g. after
    // completing a task on the detail screen) so the task list stays current.
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }
    val title = state.detail?.project?.name ?: "Project"
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showAddTask by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val editTarget = state.detail
    if (showEdit && editTarget != null) {
        EditProjectDialog(
            project = editTarget.project,
            saving = state.savingEdit,
            error = state.editError,
            onDismiss = {
                showEdit = false
                viewModel.clearEditError()
            },
            onSave = { name, description, status, dueDate ->
                viewModel.editProject(name, description, status, dueDate) { showEdit = false }
            },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.deleting) showDeleteConfirm = false },
            title = { Text("Delete project?") },
            text = { Text("This permanently deletes the project and its tasks. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteProject { onBack() } },
                    enabled = !state.deleting,
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !state.deleting) { Text("Cancel") }
            },
        )
    }

    val detailForDialog = state.detail
    if (showAddTask && detailForDialog != null) {
        AddTaskDialog(
            creating = state.creatingTask,
            error = state.createTaskError,
            assignableUsers = detailForDialog.assignableUsers,
            sections = detailForDialog.sections,
            onDismiss = {
                showAddTask = false
                viewModel.clearCreateTaskError()
            },
            onCreate = { taskTitle, status, priority, description, assignedTo, sectionId, dueDate,
                         recurrenceFrequency, recurrenceInterval, collaboratorIds ->
                viewModel.createTask(
                    title = taskTitle,
                    status = status,
                    priority = priority,
                    description = description,
                    assignedTo = assignedTo,
                    sectionId = sectionId,
                    dueDate = dueDate,
                    recurrenceFrequency = recurrenceFrequency,
                    recurrenceInterval = recurrenceInterval,
                    collaboratorIds = collaboratorIds,
                ) { showAddTask = false }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit project")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete project")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.detail != null) {
                FloatingActionButton(onClick = { showAddTask = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        },
    ) { padding ->
        val detail = state.detail
        Box(Modifier.fillMaxSize()) {
        when {
            state.loading && detail == null -> SkeletonList(Modifier.padding(padding))
            state.error != null && detail == null -> ErrorView(
                message = state.error!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            detail != null -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                item {
                    ProjectHeader(detail.project)
                }

                if (detail.hasSections) {
                    detail.sections.forEach { section ->
                        item(key = "section-${section.id}") {
                            TaskSectionHeader(section.name, section.tasks.size)
                        }
                        items(section.tasks, key = { "task-${it.id}" }) { task ->
                            TaskListItem(
                                task = task,
                                onClick = { onTaskClick(task.projectId, task.id) },
                                onToggleComplete = { viewModel.toggleComplete(task) },
                                showProject = false,
                            )
                            ThinDivider(startIndent = 52.dp)
                        }
                    }
                } else {
                    items(detail.tasks, key = { "task-${it.id}" }) { task ->
                        TaskListItem(
                            task = task,
                            onClick = { onTaskClick(task.projectId, task.id) },
                            onToggleComplete = { viewModel.toggleComplete(task) },
                            showProject = false,
                        )
                        ThinDivider(startIndent = 52.dp)
                    }
                }
            }
        }
        ConfettiEffect(burstKey = state.celebrations, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ProjectHeader(project: Project) {
    val accent = projectDotColor(project.id)
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = project.name.trim().take(1).uppercase().ifBlank { "#" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                ProjectStatusBadge(project.statusEnum)
            }
        }

        project.description?.takeIf { it.isNotBlank() }?.let { description ->
            HtmlText(
                html = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
            Spacer(Modifier.size(10.dp))
            Text(
                text = "${project.completedTasksCount}/${project.tasksCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (project.owner != null) {
                UserAvatar(project.owner, size = 24.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = project.owner.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DateUtils.formatDate(project.dueDate)?.let { due ->
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Due $due",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TaskSectionHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
