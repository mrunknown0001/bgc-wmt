package com.wmt.app.ui.taskdetail

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.wmt.app.ui.components.ConfettiEffect
import com.wmt.app.ui.components.CompletionCircle
import com.wmt.app.ui.components.ErrorView
import com.wmt.app.ui.components.HtmlText
import com.wmt.app.ui.components.PriorityBadge
import com.wmt.app.ui.components.SkeletonList
import com.wmt.app.ui.components.TaskStatusChip
import com.wmt.app.ui.components.UserAvatar
import com.wmt.app.ui.components.RestrictionNotice
import com.wmt.app.ui.components.WmtTopAppBar
import com.wmt.app.util.CameraCapture
import com.wmt.app.util.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onOpenTimesheet: ((taskId: Int) -> Unit)? = null,
    onOpenTask: (projectId: Int, taskId: Int) -> Unit = { _, _ -> },
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Comment/status failures happen while content is on screen — surface them as a
    // toast instead of silently swallowing the server's message.
    val toastContext = LocalContext.current
    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null && state.detail != null) {
            Toast.makeText(toastContext, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

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

    LaunchedEffect(state.notice) {
        val message = state.notice
        if (message != null) {
            Toast.makeText(toastContext, message, Toast.LENGTH_LONG).show()
            viewModel.clearNotice()
        }
    }

    val tagsDetail = state.detail
    if (state.editingTags && tagsDetail != null) {
        TagEditorDialog(
            current = tagsDetail.tags,
            suggestions = state.tagSuggestions,
            saving = state.savingTags,
            onDismiss = viewModel::closeTagEditor,
            onSave = viewModel::saveTags,
        )
    }

    state.previousValue?.let { sheet ->
        PreviousValueDialog(sheet = sheet, onDismiss = viewModel::dismissPreviousValue)
    }

    val fieldsDetail = state.detail
    if (state.editingFields && fieldsDetail != null) {
        CustomFieldsEditorDialog(
            fields = fieldsDetail.customFields,
            saving = state.savingFields,
            fieldErrors = state.fieldErrors,
            onDismiss = viewModel::closeFieldEditor,
            onSave = viewModel::saveCustomFields,
        )
    }

    state.pausePreview?.let { preview ->
        PauseClockDialog(
            preview = preview,
            onConfirm = viewModel::confirmPause,
            onDismiss = viewModel::dismissPause,
        )
    }

    val editing = state.detail
    if (showEdit && editing != null) {
        EditTaskDialog(
            task = editing.task,
            members = editing.members,
            canChangeAssignmentAndDates = editing.rules.canChangeAssignmentAndDates,
            saving = state.savingEdit,
            error = state.editError,
            onDismiss = {
                showEdit = false
                viewModel.clearEditError()
            },
            onSave = { title, status, priority, description, assignedTo, dueDate, startDate,
                       recurrenceFrequency, recurrenceInterval, collaboratorIds ->
                viewModel.editTask(
                    title = title,
                    status = status,
                    priority = priority,
                    description = description,
                    assignedTo = assignedTo,
                    dueDate = dueDate,
                    startDate = startDate,
                    recurrenceFrequency = recurrenceFrequency,
                    recurrenceInterval = recurrenceInterval,
                    collaboratorIds = collaboratorIds,
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
                    val rules = state.detail?.rules
                    // Drawn from the server's answer: an edit the server would refuse is
                    // not offered, rather than offered and then refused.
                    if (rules?.canEdit == true && !rules.projectIsClosed) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit task")
                        }
                    }
                    if (rules?.canDelete == true) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete task")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val detail = state.detail
        Box(Modifier.fillMaxSize()) {
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
                    comments = state.allComments,
                    hasMoreComments = state.hasMoreComments,
                    loadingMoreComments = state.loadingMoreComments,
                    maxUploadMb = state.maxUploadMb,
                    updatingStatus = state.updatingStatus,
                    postingComment = state.postingComment,
                    contentPadding = innerPadding,
                    onStatusSelected = viewModel::updateStatus,
                    onAddComment = viewModel::addComment,
                    onLoadMoreComments = viewModel::loadOlderComments,
                    onToggleSubtask = viewModel::toggleSubtask,
                    onOpenSubtask = onOpenTask,
                    onAddSubtask = viewModel::addSubtask,
                    addingSubtask = state.addingSubtask,
                    subtaskError = state.subtaskError,
                    onDismissSubtaskError = viewModel::dismissSubtaskError,
                    clockBusy = state.clockBusy,
                    onStartClock = viewModel::startClock,
                    onRequestPause = viewModel::requestPause,
                    onResumeClock = viewModel::resumeClock,
                    onOpenTimesheet = onOpenTimesheet?.let { open -> { open(viewModel.taskId) } },
                    activities = state.allActivities,
                    hasMoreActivities = state.hasMoreActivities,
                    loadingMoreActivities = state.loadingMoreActivities,
                    onLoadMoreActivities = viewModel::loadOlderActivities,
                    onShowPreviousValue = viewModel::showPreviousValue,
                    onEditFields = viewModel::openFieldEditor,
                    currentUserId = state.currentUserId,
                    deletingCommentId = state.deletingCommentId,
                    onDeleteComment = viewModel::deleteComment,
                    onEditTags = viewModel::openTagEditor,
                )
            }
        }
        ConfettiEffect(burstKey = state.celebrations, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TaskDetailContent(
    detail: TaskDetail,
    comments: List<Comment>,
    hasMoreComments: Boolean,
    loadingMoreComments: Boolean,
    maxUploadMb: Int,
    updatingStatus: Boolean,
    postingComment: Boolean,
    contentPadding: PaddingValues,
    onStatusSelected: (TaskStatus) -> Unit,
    onAddComment: (body: String, attachments: List<String>, onSuccess: () -> Unit) -> Unit,
    onLoadMoreComments: () -> Unit,
    onToggleSubtask: (subtaskId: Int, done: Boolean) -> Unit,
    onOpenSubtask: (projectId: Int, taskId: Int) -> Unit,
    onAddSubtask: (title: String) -> Unit = {},
    addingSubtask: Boolean = false,
    subtaskError: String? = null,
    onDismissSubtaskError: () -> Unit = {},
    clockBusy: Boolean,
    onStartClock: () -> Unit,
    onRequestPause: () -> Unit,
    onResumeClock: () -> Unit,
    onOpenTimesheet: (() -> Unit)? = null,
    activities: List<Activity> = detail.activities,
    hasMoreActivities: Boolean = false,
    loadingMoreActivities: Boolean = false,
    onLoadMoreActivities: () -> Unit = {},
    onShowPreviousValue: (activityId: Int) -> Unit = {},
    onEditFields: () -> Unit = {},
    currentUserId: Int? = null,
    deletingCommentId: Int? = null,
    onDeleteComment: (commentId: Int) -> Unit = {},
    onEditTags: () -> Unit = {},
) {
    val task = detail.task
    val rules = detail.rules
    var confirmDeleteComment by remember { mutableStateOf<Int?>(null) }
    confirmDeleteComment?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteComment = null },
            title = { Text("Delete comment?") },
            text = { Text("It is removed for everyone, with its attachments.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteComment = null
                    onDeleteComment(id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteComment = null }) { Text("Cancel") } },
        )
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var commentValue by remember { mutableStateOf(TextFieldValue("")) }
    var mentions by remember { mutableStateOf<List<UserSummary>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var timelineFilter by remember { mutableStateOf(TimelineFilter.ALL) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val (accepted, tooBig) = uris.partition { fitsUploadLimit(context, it, maxUploadMb) }
            if (tooBig.isNotEmpty()) {
                Toast.makeText(
                    context,
                    "Too large — videos are limited to 50MB, other files to ${maxUploadMb}MB",
                    Toast.LENGTH_LONG,
                ).show()
            }
            selectedFiles = (selectedFiles + accepted).distinct().take(5)
        }
    }

    // Camera capture: take photo → fetch a location fix → burn timestamp/geotag overlay + EXIF.
    var pendingPhoto by remember { mutableStateOf<CameraCapture.PendingPhoto?>(null) }
    var processingPhoto by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val photo = pendingPhoto
        pendingPhoto = null
        if (saved && photo != null) {
            processingPhoto = true
            scope.launch {
                runCatching {
                    CameraCapture.stampPhoto(photo.file, CameraCapture.currentLocation(context))
                }.onFailure {
                    Toast.makeText(context, "Couldn't process photo", Toast.LENGTH_SHORT).show()
                }
                selectedFiles = (selectedFiles + photo.uri).distinct().take(5)
                processingPhoto = false
            }
        }
    }
    val launchCamera = {
        val photo = CameraCapture.newPendingPhoto(context)
        pendingPhoto = photo
        cameraLauncher.launch(photo.uri)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { launchCamera() } // proceed either way — the photo just won't be geotagged if denied

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
                // Closing is held back by an open dependency or a missing attachment; the
                // notices below say which, so the button is never refused unexplained.
                val closeHeld = !done && (rules.isBlocked || rules.closeNeedsAttachment)
                MarkCompleteButton(
                    done = done,
                    enabled = !updatingStatus && !closeHeld && !rules.projectIsClosed && rules.canEdit,
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
                if (rules.projectIsClosed) {
                    RestrictionNotice("This project is closed, so its tasks can't be changed.")
                }
                if (!done && rules.closeNeedsAttachment) {
                    RestrictionNotice(
                        "This project needs proof of work: add an attachment in a comment before closing this task.",
                    )
                }
                if (rules.isBlocked || detail.dependents.isNotEmpty()) {
                    DependencyNotices(
                        blockedBy = detail.dependencies,
                        blocking = detail.dependents,
                        isBlocked = rules.isBlocked,
                        onOpen = { id -> onOpenSubtask(task.projectId, id) },
                    )
                }
            }
        }

        item(key = "clock") {
            TaskClockStrip(
                clock = detail.clock,
                busy = clockBusy,
                onStart = onStartClock,
                onPause = onRequestPause,
                onResume = onResumeClock,
                onOpenTimesheet = onOpenTimesheet,
                modifier = Modifier.padding(bottom = 4.dp),
            )
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
                task.completedAt?.let { completedAt ->
                    val late = DateUtils.isCompletedLate(task.dueDate, completedAt)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(
                            text = DateUtils.formatDateTime(completedAt) ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (late) {
                            Text(
                                text = "Late",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                task.recurrenceLabel?.let { label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Repeats",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                        )
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = label.removePrefix("Repeats "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (task.collaborators.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Collaborators",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            task.collaborators.forEach { user ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    UserAvatar(user = user, size = 22.dp)
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val canTag = rules.canEdit && !rules.projectIsClosed
        if (detail.tags.isNotEmpty() || canTag) {
            item(key = "tags") { TagChips(detail.tags, onEdit = if (canTag) onEditTags else null) }
        }

        if (detail.customFields.isNotEmpty()) {
            item(key = "custom-fields") {
                CustomFieldsSection(
                    fields = detail.customFields,
                    canEdit = rules.canEditCustomFields && !rules.projectIsClosed,
                    onEdit = onEditFields,
                )
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

        // The header shows even with no subtasks: the way to add the first one lives
        // under it, and so does the reason when there cannot be one.
        item(key = "subtasks-header") {
            val done = detail.subtasks.count { it.statusEnum == TaskStatus.DONE }
            Text(
                text = if (detail.subtasks.isEmpty()) {
                    "Subtasks"
                } else {
                    "Subtasks ($done/${detail.subtasks.size})"
                },
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
        item(key = "add-subtask") {
            // Where the control would be, the server's reason stands instead, in its own
            // words: restating it here would be a second copy of the rule.
            val restriction = rules.subtaskRestriction
            if (restriction != null) {
                RestrictionNotice(restriction)
            } else {
                AddSubtaskControl(
                    busy = addingSubtask,
                    error = subtaskError,
                    enabled = rules.canEdit && !rules.projectIsClosed,
                    onAdd = onAddSubtask,
                    onDismissError = onDismissSubtaskError,
                )
            }
        }

        item(key = "comments-header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Comments & Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimelineFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = timelineFilter == filter,
                            onClick = { timelineFilter = filter },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
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
                        val label = remember(uri) {
                            attachmentDisplayName(context, uri) ?: "Attachment ${index + 1}"
                        }
                        Text(
                            text = label,
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
                        onClick = { filePicker.launch(ATTACHMENT_MIME_TYPES) },
                        enabled = !postingComment && selectedFiles.size < 5,
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                    }
                    IconButton(
                        onClick = {
                            if (CameraCapture.hasLocationPermission(context)) {
                                launchCamera()
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                        enabled = !postingComment && !processingPhoto && selectedFiles.size < 5,
                    ) {
                        if (processingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Take photo")
                        }
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
                            // Clear the draft only once the server accepts it, so a
                            // rejected upload doesn't eat the comment and attachments.
                            onAddComment(body, selectedFiles.map { it.toString() }) {
                                commentValue = TextFieldValue("")
                                mentions = emptyList()
                                selectedFiles = emptyList()
                            }
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

        if (timelineFilter != TimelineFilter.ACTIVITY) {
            if (comments.isEmpty()) {
                item(key = "comments-empty") {
                    Text(
                        text = "No comments yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(comments, key = { "comment-${it.id}" }) { comment ->
                    // Offered on your own comments only: anyone else's is a 403 on the server.
                    val mine = currentUserId != null && comment.author?.id == currentUserId
                    CommentRow(
                        comment = comment,
                        onDelete = if (mine) ({ confirmDeleteComment = comment.id }) else null,
                        deleting = deletingCommentId == comment.id,
                    )
                }
                if (hasMoreComments) {
                    item(key = "comments-load-more") {
                        TextButton(
                            onClick = onLoadMoreComments,
                            enabled = !loadingMoreComments,
                        ) {
                            if (loadingMoreComments) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Show older comments")
                        }
                    }
                }
            }
        }

        if (timelineFilter != TimelineFilter.COMMENTS && activities.isNotEmpty()) {
            if (timelineFilter == TimelineFilter.ALL) {
                item(key = "activity-header") {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(activities, key = { "activity-${it.id}" }) { activity ->
                ActivityRow(
                    activity = activity,
                    onShowPrevious = { onShowPreviousValue(activity.id) },
                )
            }
            if (hasMoreActivities) {
                item(key = "activity-load-more") {
                    TextButton(onClick = onLoadMoreActivities, enabled = !loadingMoreActivities) {
                        if (loadingMoreActivities) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Show older activity")
                    }
                }
            }
        }
    }
}

private enum class TimelineFilter(val label: String) {
    ALL("All"),
    COMMENTS("Comments"),
    ACTIVITY("Activity"),
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

/**
 * Adds one subtask: a quiet "Add subtask" line that opens a single field.
 *
 * Only a title is asked for. Everything else a task can carry is a trip to the task
 * itself, and a subtask is usually written down mid-thought, so the cost of adding one
 * should be a line of text and nothing more.
 */
@Composable
private fun AddSubtaskControl(
    busy: Boolean,
    error: String?,
    enabled: Boolean,
    onAdd: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // The field clears only once the server has taken it, so a refusal leaves the
    // typing intact to correct rather than making it be typed again.
    LaunchedEffect(busy, error) {
        if (!busy && error == null && open && title.isNotBlank()) title = ""
    }
    LaunchedEffect(open) {
        if (open) runCatching { focusRequester.requestFocus() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!open) {
            TextButton(onClick = { open = true }, enabled = enabled) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add subtask")
            }
        } else {
            val submit = {
                if (title.isNotBlank() && !busy) {
                    onDismissError()
                    onAdd(title)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (error != null) onDismissError()
                    },
                    placeholder = { Text("Subtask title") },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = submit, enabled = title.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add subtask")
                    }
                }
                IconButton(
                    onClick = {
                        open = false
                        title = ""
                        onDismissError()
                    },
                    enabled = !busy,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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
private fun CommentRow(
    comment: Comment,
    onDelete: (() -> Unit)? = null,
    deleting: Boolean = false,
) {
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
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    if (deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete comment",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
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
private fun ActivityRow(activity: Activity, onShowPrevious: () -> Unit = {}) {
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
        // The line above is cut at 255 characters; this is the way to the whole answer.
        if (activity.hasPreviousValue) {
            TextButton(
                onClick = onShowPrevious,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) { Text("Show previous text", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** Types the comment attachment picker offers (mirrors the backend's allowlist). */
private val ATTACHMENT_MIME_TYPES = arrayOf(
    "image/*",
    "application/pdf",
    "video/*",
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)

private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024

/** Client-side size check: videos ≤ 50MB, other files ≤ the server's max_upload_size setting. */
private fun fitsUploadLimit(context: Context, uri: Uri, maxUploadMb: Int): Boolean {
    val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }
        ?: return true // size unknown — let the server decide
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return if (mime.startsWith("video/")) {
        size <= MAX_VIDEO_BYTES
    } else {
        // Oversized images get transcoded/downscaled at upload; only hard-reject other types.
        mime.startsWith("image/") || size <= maxUploadMb.toLong() * 1024 * 1024
    }
}

private fun attachmentDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
