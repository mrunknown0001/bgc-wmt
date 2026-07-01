package com.wmt.app.ui.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.ProjectDetail
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.repository.ProjectRepository
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectDetailUiState(
    val loading: Boolean = true,
    val detail: ProjectDetail? = null,
    val error: String? = null,
    val creatingTask: Boolean = false,
    val createTaskError: String? = null,
    val savingEdit: Boolean = false,
    val editError: String? = null,
    val deleting: Boolean = false,
)

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val taskRepository: TaskRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Int = checkNotNull(savedStateHandle["projectId"])

    private val _state = MutableStateFlow(ProjectDetailUiState())
    val state: StateFlow<ProjectDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.detail == null, error = null) }
        viewModelScope.launch {
            when (val result = repository.projectDetail(projectId)) {
                is Resource.Success -> _state.update {
                    it.copy(loading = false, error = null, detail = result.data)
                }
                is Resource.Error -> _state.update {
                    it.copy(
                        loading = false,
                        error = result.error.message,
                        detail = result.data ?: it.detail,
                    )
                }
                is Resource.Loading -> _state.update {
                    it.copy(loading = it.detail == null && result.data == null, detail = result.data ?: it.detail)
                }
            }
        }
    }

    /** Tap-to-complete from the project view: flip done state optimistically, then reconcile. */
    fun toggleComplete(task: Task) {
        val newStatus = if (task.statusEnum == TaskStatus.DONE) TaskStatus.TO_DO else TaskStatus.DONE
        _state.update { st ->
            val detail = st.detail ?: return@update st
            fun List<Task>.flip() = map { if (it.id == task.id) it.copy(status = newStatus.raw) else it }
            st.copy(
                detail = detail.copy(
                    tasks = detail.tasks.flip(),
                    sections = detail.sections.map { it.copy(tasks = it.tasks.flip()) },
                ),
            )
        }
        viewModelScope.launch {
            taskRepository.updateStatus(projectId, task.id, newStatus.raw)
            load()
        }
    }

    fun clearCreateTaskError() = _state.update { it.copy(createTaskError = null) }

    fun clearEditError() = _state.update { it.copy(editError = null) }

    fun editProject(
        name: String,
        description: String?,
        status: String,
        dueDate: String?,
        onSuccess: () -> Unit,
    ) {
        _state.update { it.copy(savingEdit = true, editError = null) }
        viewModelScope.launch {
            when (val result = repository.updateProject(projectId, name, description, status, dueDate)) {
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

    fun deleteProject(onSuccess: () -> Unit) {
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteProject(projectId)) {
                is Resource.Success -> onSuccess()
                is Resource.Error -> _state.update {
                    it.copy(deleting = false, error = result.error.message)
                }
                is Resource.Loading -> _state.update { it.copy(deleting = false) }
            }
        }
    }

    fun createTask(
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        sectionId: Int?,
        dueDate: String?,
        onSuccess: () -> Unit,
    ) {
        _state.update { it.copy(creatingTask = true, createTaskError = null) }
        viewModelScope.launch {
            when (
                val result = taskRepository.createTask(
                    projectId = projectId,
                    title = title,
                    status = status,
                    priority = priority,
                    description = description,
                    assignedTo = assignedTo,
                    sectionId = sectionId,
                    dueDate = dueDate,
                )
            ) {
                is Resource.Success -> {
                    _state.update { it.copy(creatingTask = false) }
                    load()
                    onSuccess()
                }
                is Resource.Error -> _state.update {
                    it.copy(creatingTask = false, createTaskError = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
