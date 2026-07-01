package com.wmt.app.ui.taskdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.Activity
import com.wmt.app.domain.model.Comment
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.model.UserSummary
import com.wmt.app.ui.components.AttachmentView
import com.wmt.app.ui.components.CompletionCircle
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.HtmlText
import com.wmt.app.ui.components.PriorityBadge
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.TaskStatusChip
import com.wmt.app.ui.components.UserAvatar
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.DateUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onOpenTask: (projectId: Int, taskId: Int) -> Unit = { _, _ -> },
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete task?") },
            text = { Text("This permanently deletes the task. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteTask { onBack() }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // Near-real-time: silently refresh comments/activity while the task is on screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            viewModel.poll()
        }
    }

    val editing = state.detail
    if (showEdit && editing != null) {
        EditTaskDialog(
            task = editing.task,
            members = editing.members,
            saving = state.savingEdit,
            error = state.editError,
            onDismiss = {
                showEdit = false
                viewModel.clearEditError()
            },
            onSave = { title, status, priority, description, assignedTo, dueDate, startDate ->
                viewModel.editTask(
                    title = title,
                    status = status,
                    priority = priority,
                    description = description,
                    assignedTo = assignedTo,
                    dueDate = dueDate,
                    startDate = startDate,
                ) { showEdit = false }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = state.detail?.task?.title ?: "Task",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit task")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete task")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val detail = state.detail
        when {
            state.loading && detail == null -> {
                SkeletonList(modifier = Modifier.padding(innerPadding))
            }
            state.error != null && detail == null -> {
                ErrorView(
                    message = state.error!!,
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            detail != null -> {
                TaskDetailContent(
                    detail = detail,
                    updatingStatus = state.updatingStatus,
                    postingComment = state.postingComment,
                    contentPadding = innerPadding,
                    onStatusSelected = viewModel::updateStatus,
                    onAddComment = viewModel::addComment,
                    onToggleSubtask = viewModel::toggleSubtask,
                    onOpenSubtask = onOpenTask,
                )
            }
        }
    }
}

@Composable
private fun TaskDetailContent(
    detail: TaskDetail,
    updatingStatus: Boolean,
    postingComment: Boolean,
    contentPadding: PaddingValues,
    onStatusSelected: (TaskStatus) -> Unit,
    onAddComment: (String, List<String>) -> Unit,
    onToggleSubtask: (subtaskId: Int, done: Boolean) -> Unit,
    onOpenSubtask: (projectId: Int, taskId: Int) -> Unit,
) {
    val task = detail.task
    var commentValue by remember { mutableStateOf(TextFieldValue("")) }
    var mentions by remember { mutableStateOf<List<UserSummary>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) selectedFiles = (selectedFiles + uris).distinct().take(5)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            val done = task.statusEnum == TaskStatus.DONE
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarkCompleteButton(
                    done = done,
                    enabled = !updatingStatus,
                    onToggle = { onStatusSelected(if (done) TaskStatus.TO_DO else TaskStatus.DONE) },
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PriorityBadge(task.priorityEnum)
                    TaskStatusChip(task.statusEnum)
                }
                task.project?.name?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "description") {
            val description = task.description
            if (description.isNullOrBlank()) {
                Text(
                    text = "No description",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HtmlText(
                    html = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "meta") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Assignee",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp),
                    )
                    val assignee = task.assignee
                    if (assignee != null) {
                        UserAvatar(user = assignee, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = assignee.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = "Unassigned",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MetaRow(label = "Due date", value = DateUtils.formatDate(task.dueDate) ?: "—")
                MetaRow(label = "Start date", value = DateUtils.formatDate(task.startDate) ?: "—")
            }
        }

        item(key = "status-picker") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusDropdown(
                    current = task.statusEnum,
                    enabled = !updatingStatus,
                    onSelected = onStatusSelected,
                )
            }
        }

        if (detail.subtasks.isNotEmpty()) {
            val done = detail.subtasks.count { it.statusEnum == TaskStatus.DONE }
            item(key = "subtasks-header") {
                Text(
                    text = "Subtasks ($done/${detail.subtasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(detail.subtasks, key = { "subtask-${it.id}" }) { sub ->
                SubtaskRow(
                    task = sub,
                    onToggle = { checked -> onToggleSubtask(sub.id, checked) },
                    onClick = { onOpenSubtask(sub.projectId, sub.id) },
                )
            }
        }

        item(key = "comments-header") {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item(key = "comment-input") {
            val text = commentValue.text
            val cursor = commentValue.selection.end.coerceIn(0, text.length)
            val mentionQuery: String? = run {
                if (cursor == 0) return@run null
                val at = text.lastIndexOf('@', cursor - 1)
                if (at < 0) return@run null
                val token = text.substring(at + 1, cursor)
                if (token.any { it == ' ' || it == '\n' }) null else token
            }
            val suggestions = if (mentionQuery != null) {
                detail.members.filter { it.name.contains(mentionQuery, ignoreCase = true) }.take(5)
            } else {
                emptyList()
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (suggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Column {
                            suggestions.forEach { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val at = text.lastIndexOf('@', (cursor - 1).coerceAtLeast(0))
                                            if (at >= 0) {
                                                val newText = text.substring(0, at) + "@" + user.name + " " +
                                                    text.substring(cursor)
                                                val newCursor = at + 1 + user.name.length + 1
                                                commentValue = TextFieldValue(newText, selection = TextRange(newCursor))
                                                mentions = (mentions + user).distinctBy { it.id }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    UserAvatar(user = user, size = 28.dp)
                                    Text(user.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                selectedFiles.forEachIndexed { index, uri ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Attachment ${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { selectedFiles = selectedFiles - uri }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                        enabled = !postingComment && selectedFiles.size < 5,
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                    }
                    OutlinedTextField(
                        value = commentValue,
                        onValueChange = { commentValue = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a comment (use @ to mention)") },
                        enabled = !postingComment,
                    )
                    IconButton(
                        onClick = {
                            var body = commentValue.text
                            // Replace longer names first so overlapping names don't corrupt each other.
                            mentions.sortedByDescending { it.name.length }.forEach { user ->
                                val span = "<span class=\"mention\" data-id=\"${user.id}\" " +
                                    "data-label=\"${user.name}\">@${user.name}</span>"
                                body = body.replace("@${user.name}", span)
                            }
                            onAddComment(body, selectedFiles.map { it.toString() })
                            commentValue = TextFieldValue("")
                            mentions = emptyList()
                            selectedFiles = emptyList()
                        },
                        enabled = !postingComment && (commentValue.text.isNotBlank() || selectedFiles.isNotEmpty()),
                    ) {
                        if (postingComment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                            )
                        }
                    }
                }
            }
        }

        if (detail.comments.isEmpty()) {
            item(key = "comments-empty") {
                Text(
                    text = "No comments yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(detail.comments, key = { "comment-${it.id}" }) { comment ->
                CommentRow(comment = comment)
            }
        }

        if (detail.activities.isNotEmpty()) {
            item(key = "activity-header") {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(detail.activities, key = { "activity-${it.id}" }) { activity ->
                ActivityRow(activity = activity)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(
    current: TaskStatus,
    enabled: Boolean,
    onSelected: (TaskStatus) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TaskStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.label) },
                    onClick = {
                        expanded = false
                        if (status != current) onSelected(status)
                    },
                )
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    task: com.wmt.app.domain.model.Task,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val done = task.statusEnum == TaskStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompletionCircle(done = done, size = 20.dp, onToggle = { onToggle(!done) })
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkCompleteButton(
    done: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val base = Modifier
        .clip(shape)
        .clickable(enabled = enabled, onClick = onToggle)
    val styled = if (done) {
        base.background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        base.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    }
    Row(
        modifier = styled.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompletionCircle(done = done, size = 18.dp)
        Text(
            text = if (done) "Completed" else "Mark complete",
            style = MaterialTheme.typography.labelLarge,
            color = if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserAvatar(user = comment.author, size = 32.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = comment.author?.name ?: "Unknown",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = DateUtils.timeAgo(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (comment.body.isNotBlank()) {
                HtmlText(
                    html = comment.body,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            comment.attachments.forEach { attachment ->
                AttachmentView(attachment)
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = activity.description,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = DateUtils.timeAgo(activity.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
