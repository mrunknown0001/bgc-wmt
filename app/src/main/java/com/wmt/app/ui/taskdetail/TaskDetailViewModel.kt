package com.wmt.app.ui.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.data.remote.RealtimeClient
import com.wmt.app.domain.model.Activity
import com.wmt.app.domain.model.Comment
import com.wmt.app.domain.model.PreviousValue
import com.wmt.app.domain.model.PausePreview
import com.wmt.app.domain.model.TaskClock
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskDetailUiState(
    val loading: Boolean = true,
    val detail: TaskDetail? = null,
    val error: String? = null,
    val updatingStatus: Boolean = false,
    val postingComment: Boolean = false,
    val savingEdit: Boolean = false,
    val editError: String? = null,
    /** Comments loaded past the initial page, oldest last (appended below detail.comments). */
    val olderComments: List<Comment> = emptyList(),
    val hasMoreComments: Boolean = false,
    val loadingMoreComments: Boolean = false,
    /** Server-configured general attachment cap (videos have a fixed 50MB cap). */
    val maxUploadMb: Int = 10,
    /** A clock action is in flight; the buttons wait rather than queueing presses. */
    val clockBusy: Boolean = false,
    /** Non-null while the pause dialog is open, holding what the server would record. */
    val pausePreview: PausePreview? = null,
    /** Increments each time the user completes this task or a subtask — drives confetti. */
    val celebrations: Int = 0,
    /** Activity rows paged in past the first 20, oldest last. */
    val olderActivities: List<Activity> = emptyList(),
    val loadingMoreActivities: Boolean = false,
    /** Set once a page answers has_more = false, so the button stops being offered. */
    val activitiesExhausted: Boolean = false,
    /** The "previous text" sheet: which row it is for, and what came back. */
    val previousValue: PreviousValueSheet? = null,
    /** The custom field editor is open. */
    val editingFields: Boolean = false,
    val savingFields: Boolean = false,
    /** The server's 422, keyed by its path ("values.12"). */
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    /** A one-off line for a snackbar: an automation warning, or a field save that failed. */
    val notice: String? = null,
    /** Whose comments may be deleted from this screen: their own, as the server allows. */
    val currentUserId: Int? = null,
    /** A comment delete in flight, so its button waits rather than queueing presses. */
    val deletingCommentId: Int? = null,
    val editingTags: Boolean = false,
    val savingTags: Boolean = false,
    /** Tag names already in use, for suggestions; empty until the editor first opens. */
    val tagSuggestions: List<String> = emptyList(),
) {
    /** First page plus paged-in older rows, de-duplicated after a refresh. */
    val allActivities: List<Activity>
        get() {
            val page = detail?.activities.orEmpty()
            val oldestShown = page.minOfOrNull { it.id } ?: return page
            return page + olderActivities.filter { it.id < oldestShown }
        }

    /** More history exists than is shown: the server's total says so, until a page says otherwise. */
    val hasMoreActivities: Boolean
        get() = !activitiesExhausted && (detail?.totalActivities ?: 0) > allActivities.size

    /** Initial page plus paged-in older comments, de-duplicated after refreshes. */
    val allComments: List<Comment>
        get() {
            val page = detail?.comments.orEmpty()
            val newestOldId = page.minOfOrNull { it.id } ?: return page
            return page + olderComments.filter { it.id < newestOldId }
        }
}

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val realtimeClient: RealtimeClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Int = checkNotNull(savedStateHandle["projectId"])
    val taskId: Int = checkNotNull(savedStateHandle["taskId"])

    private val _state = MutableStateFlow(TaskDetailUiState())
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()

    init {
        load()
        // Live updates: refresh comments/activity when the task's channel fires.
        viewModelScope.launch { realtimeClient.observeTask(taskId).collect { poll() } }
        // Keep the attachment-size validation in sync with the server's setting.
        viewModelScope.launch { settingsRepository.refreshAppSettings() }
        viewModelScope.launch {
            authRepository.currentUser.collect { user -> _state.update { it.copy(currentUserId = user?.id) } }
        }
        viewModelScope.launch {
            settingsRepository.maxUploadSizeMb.collect { mb ->
                _state.update { it.copy(maxUploadMb = mb) }
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = it.detail == null, error = null) }
        viewModelScope.launch {
            when (val result = repository.taskDetail(projectId, taskId)) {
                is Resource.Success -> _state.update {
                    it.copy(
                        loading = false,
                        detail = result.data,
                        error = null,
                        // A full first page implies there may be older comments to fetch.
                        hasMoreComments = it.hasMoreComments || result.data.comments.size >= 20,
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun loadOlderComments() {
        val current = _state.value
        if (current.loadingMoreComments) return
        val beforeId = current.allComments.lastOrNull()?.id ?: return
        _state.update { it.copy(loadingMoreComments = true) }
        viewModelScope.launch {
            when (val result = repository.olderComments(projectId, taskId, beforeId)) {
                is Resource.Success -> _state.update {
                    val (comments, hasMore) = result.data
                    val known = (it.olderComments + it.detail?.comments.orEmpty()).map { c -> c.id }.toSet()
                    it.copy(
                        loadingMoreComments = false,
                        olderComments = it.olderComments + comments.filter { c -> c.id !in known },
                        hasMoreComments = hasMore,
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(loadingMoreComments = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun loadOlderActivities() {
        val current = _state.value
        if (current.loadingMoreActivities) return
        val beforeId = current.allActivities.lastOrNull()?.id ?: return
        _state.update { it.copy(loadingMoreActivities = true) }
        viewModelScope.launch {
            when (val result = repository.olderActivities(projectId, taskId, beforeId)) {
                is Resource.Success -> _state.update {
                    val (rows, hasMore) = result.data
                    val known = it.allActivities.map { a -> a.id }.toSet()
                    it.copy(
                        loadingMoreActivities = false,
                        olderActivities = it.olderActivities + rows.filter { a -> a.id !in known },
                        activitiesExhausted = !hasMore,
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(loadingMoreActivities = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Opens the sheet for one activity row and fetches the text it replaced. */
    fun showPreviousValue(activityId: Int) {
        _state.update { it.copy(previousValue = PreviousValueSheet(activityId = activityId, loading = true)) }
        viewModelScope.launch {
            val result = repository.previousValue(activityId)
            _state.update { state ->
                // The sheet may have been closed, or opened for another row, meanwhile.
                if (state.previousValue?.activityId != activityId) return@update state
                state.copy(
                    previousValue = when (result) {
                        is Resource.Success -> PreviousValueSheet(activityId, loading = false, value = result.data)
                        is Resource.Error -> PreviousValueSheet(activityId, loading = false, error = result.error.message)
                        is Resource.Loading -> state.previousValue
                    },
                )
            }
        }
    }

    fun dismissPreviousValue() = _state.update { it.copy(previousValue = null) }

    fun openTagEditor() {
        _state.update { it.copy(editingTags = true) }
        if (_state.value.tagSuggestions.isEmpty()) {
            viewModelScope.launch {
                val result = repository.tagSuggestions()
                if (result is Resource.Success) _state.update { it.copy(tagSuggestions = result.data) }
            }
        }
    }

    fun closeTagEditor() = _state.update { it.copy(editingTags = false) }

    /** Sets the task's tags to exactly [names], then refreshes. */
    fun saveTags(names: List<String>) {
        _state.update { it.copy(savingTags = true) }
        viewModelScope.launch {
            when (val result = repository.setTags(taskId, names)) {
                is Resource.Success -> {
                    _state.update { state ->
                        state.copy(
                            savingTags = false,
                            editingTags = false,
                            detail = state.detail?.copy(tags = result.data),
                        )
                    }
                    poll()
                }
                is Resource.Error -> _state.update { it.copy(savingTags = false, notice = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * Deletes one of the user's own comments, then refreshes. Removed from the paged-in
     * older list straight away, because a refresh only replaces the first page.
     */
    fun deleteComment(commentId: Int) {
        if (_state.value.deletingCommentId != null) return
        _state.update { it.copy(deletingCommentId = commentId) }
        viewModelScope.launch {
            when (val result = repository.deleteComment(projectId, taskId, commentId)) {
                is Resource.Success -> {
                    _state.update { state ->
                        state.copy(
                            deletingCommentId = null,
                            olderComments = state.olderComments.filter { it.id != commentId },
                            detail = state.detail?.let { d -> d.copy(comments = d.comments.filter { it.id != commentId }) },
                        )
                    }
                    poll()
                }
                is Resource.Error -> _state.update {
                    it.copy(deletingCommentId = null, notice = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun openFieldEditor() = _state.update { it.copy(editingFields = true, fieldErrors = emptyMap()) }

    fun closeFieldEditor() = _state.update { it.copy(editingFields = false, fieldErrors = emptyMap()) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /**
     * Saves the fields that changed, then reloads the task: the save writes activity rows
     * and may fire automation rules that change the task, so the screen redraws from what
     * the server holds rather than from what was typed. A 422 keeps the editor open with
     * the server's message beside the field it names.
     */
    fun saveCustomFields(changes: Map<Int, Any?>) {
        if (changes.isEmpty()) {
            closeFieldEditor()
            return
        }
        _state.update { it.copy(savingFields = true, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            when (val result = repository.saveCustomFields(projectId, taskId, changes)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(savingFields = false, editingFields = false, notice = result.data)
                    }
                    poll()
                }
                is Resource.Error -> _state.update {
                    val validation = result.error as? AppError.Validation
                    it.copy(
                        savingFields = false,
                        fieldErrors = validation?.fieldErrors.orEmpty(),
                        // A refusal not tied to a field (a 403, a stale lock) still needs saying.
                        notice = if (validation == null) result.error.message else null,
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Silent background refresh for near-real-time comment/activity updates (no spinner). */
    fun poll() {
        viewModelScope.launch {
            val result = repository.taskDetail(projectId, taskId)
            if (result is Resource.Success) {
                _state.update { it.copy(detail = result.data) }
            }
        }
    }

    /** The clock as the last response left it. */
    private val clock get() = _state.value.detail?.clock ?: TaskClock()

    fun startClock() = runClockAction { repository.startClock(projectId, taskId, clock) }

    fun resumeClock() = runClockAction { repository.resumeClock(projectId, taskId, clock) }

    /**
     * Opens the pause dialog with the server's own suggestion.
     *
     * The figure is asked for rather than counted here: the server knows what has
     * already been logged today and whose day it lands on, neither of which the phone
     * can work out.
     */
    fun requestPause() {
        if (_state.value.clockBusy) return
        _state.update { it.copy(clockBusy = true) }
        viewModelScope.launch {
            when (val result = repository.pausePreview(projectId, taskId)) {
                is Resource.Success -> _state.update {
                    it.copy(clockBusy = false, pausePreview = result.data)
                }
                is Resource.Error -> _state.update {
                    it.copy(clockBusy = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun dismissPause() = _state.update { it.copy(pausePreview = null) }

    fun confirmPause(minutes: Int, note: String?) {
        _state.update { it.copy(pausePreview = null) }
        runClockAction { repository.pauseClock(projectId, taskId, minutes, note, clock) }
    }

    /**
     * Runs a clock call and folds the reply into the detail on screen.
     *
     * Followed by a reload because starting also moves the task to in progress, and a
     * pause writes a time log: both change more of the record than the reply carries.
     */
    private fun runClockAction(action: suspend () -> Resource<TaskClock>) {
        if (_state.value.clockBusy) return
        _state.update { it.copy(clockBusy = true) }
        viewModelScope.launch {
            when (val result = action()) {
                is Resource.Success -> {
                    _state.update { state ->
                        state.copy(
                            clockBusy = false,
                            detail = state.detail?.copy(clock = result.data),
                        )
                    }
                    poll()
                }
                is Resource.Error -> _state.update {
                    it.copy(clockBusy = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun clearEditError() = _state.update { it.copy(editError = null) }

    /** Consumed by the screen after surfacing a transient (toast) error. */
    fun clearError() = _state.update { it.copy(error = null) }

    fun deleteTask(onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.deleteTask(projectId, taskId)) {
                is Resource.Success -> onSuccess()
                is Resource.Error -> _state.update { it.copy(error = result.error.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Toggles a subtask's done state via the patch endpoint, then refreshes. */
    fun toggleSubtask(subtaskId: Int, done: Boolean) {
        if (done) _state.update { it.copy(celebrations = it.celebrations + 1) }
        viewModelScope.launch {
            repository.updateStatus(projectId, subtaskId, if (done) "done" else "to_do")
            load()
        }
    }

    fun editTask(
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        dueDate: String?,
        startDate: String?,
        recurrenceFrequency: String? = null,
        recurrenceInterval: Int? = null,
        collaboratorIds: List<Int> = emptyList(),
        onSuccess: () -> Unit,
    ) {
        _state.update { it.copy(savingEdit = true, editError = null) }
        viewModelScope.launch {
            when (
                val result = repository.updateTask(
                    projectId = projectId,
                    taskId = taskId,
                    title = title,
                    status = status,
                    priority = priority,
                    description = description,
                    assignedTo = assignedTo,
                    dueDate = dueDate,
                    startDate = startDate,
                    isRecurring = recurrenceFrequency != null,
                    recurrenceFrequency = recurrenceFrequency,
                    recurrenceInterval = if (recurrenceFrequency != null) recurrenceInterval ?: 1 else null,
                    // Always send the list so clearing every collaborator syncs to empty.
                    collaboratorIds = collaboratorIds,
                )
            ) {
                is Resource.Success -> {
                    _state.update { it.copy(savingEdit = false) }
                    load()
                    onSuccess()
                }
                is Resource.Error -> _state.update {
                    it.copy(savingEdit = false, editError = result.error.message)
                }
                is Resource.Loading -> _state.update { it.copy(savingEdit = false) }
            }
        }
    }

    fun updateStatus(status: TaskStatus) {
        val current = _state.value.detail
        if (current == null) {
            load()
            return
        }
        _state.update {
            it.copy(
                updatingStatus = true,
                celebrations = it.celebrations +
                    if (status == TaskStatus.DONE && current.task.statusEnum != TaskStatus.DONE) 1 else 0,
            )
        }
        viewModelScope.launch {
            when (val result = repository.updateStatus(projectId, taskId, status.raw)) {
                is Resource.Success -> _state.update {
                    val detail = it.detail
                    it.copy(
                        updatingStatus = false,
                        detail = detail?.copy(task = result.data),
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(updatingStatus = false, error = result.error.message)
                }
                is Resource.Loading -> _state.update {
                    it.copy(updatingStatus = false)
                }
            }
        }
    }

    fun addComment(
        body: String,
        attachmentUris: List<String> = emptyList(),
        onSuccess: () -> Unit = {},
    ) {
        if (body.isBlank() && attachmentUris.isEmpty()) return
        _state.update { it.copy(postingComment = true) }
        viewModelScope.launch {
            when (val result = repository.addComment(projectId, taskId, body, attachmentUris)) {
                is Resource.Success -> {
                    _state.update { it.copy(postingComment = false) }
                    load()
                    onSuccess()
                }
                is Resource.Error -> _state.update {
                    it.copy(postingComment = false, error = result.error.message)
                }
                is Resource.Loading -> _state.update {
                    it.copy(postingComment = false)
                }
            }
        }
    }
}

/** What the "previous text" sheet is showing for one activity row. */
data class PreviousValueSheet(
    val activityId: Int,
    val loading: Boolean = false,
    val value: PreviousValue? = null,
    val error: String? = null,
)
