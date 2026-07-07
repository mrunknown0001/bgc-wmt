package com.wmt.app.ui.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.data.remote.RealtimeClient
import com.wmt.app.domain.model.Comment
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.domain.repository.TaskRepository
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
    /** Increments each time the user completes this task or a subtask — drives confetti. */
    val celebrations: Int = 0,
) {
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

    /** Silent background refresh for near-real-time comment/activity updates (no spinner). */
    fun poll() {
        viewModelScope.launch {
            val result = repository.taskDetail(projectId, taskId)
            if (result is Resource.Success) {
                _state.update { it.copy(detail = result.data) }
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
