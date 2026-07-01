package com.wmt.app.ui.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.data.remote.RealtimeClient
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.model.TaskStatus
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
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repository: TaskRepository,
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
    }

    fun load() {
        _state.update { it.copy(loading = it.detail == null, error = null) }
        viewModelScope.launch {
            when (val result = repository.taskDetail(projectId, taskId)) {
                is Resource.Success -> _state.update {
                    it.copy(loading = false, detail = result.data, error = null)
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, error = result.error.message)
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
        _state.update { it.copy(updatingStatus = true) }
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

    fun addComment(body: String, attachmentUris: List<String> = emptyList()) {
        if (body.isBlank() && attachmentUris.isEmpty()) return
        _state.update { it.copy(postingComment = true) }
        viewModelScope.launch {
            when (val result = repository.addComment(projectId, taskId, body, attachmentUris)) {
                is Resource.Success -> {
                    _state.update { it.copy(postingComment = false) }
                    load()
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
