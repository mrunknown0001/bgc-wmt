package com.wmt.app.ui.mytasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskStatus
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.util.DateUtils
import com.wmt.app.util.NetworkMonitor
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskGroup(
    val title: String,
    val tasks: List<Task>,
)

data class MyTasksUiState(
    val loading: Boolean = true,
    val groups: List<TaskGroup> = emptyList(),
    val error: String? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val isEmpty: Boolean = false,
)

@HiltViewModel
class MyTasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(MyTasksUiState())
    val state: StateFlow<MyTasksUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(isRefresh = false)
        observeConnectivity()
    }

    fun refresh() = load(isRefresh = true)

    /** Tap-to-complete from the list: flip the task's done state optimistically, then reconcile. */
    fun toggleComplete(task: Task) {
        val newStatus = if (task.statusEnum == TaskStatus.DONE) TaskStatus.TO_DO else TaskStatus.DONE
        _state.update { st ->
            st.copy(
                groups = st.groups.map { group ->
                    group.copy(
                        tasks = group.tasks.map {
                            if (it.id == task.id) it.copy(status = newStatus.raw) else it
                        },
                    )
                },
            )
        }
        viewModelScope.launch {
            repository.updateStatus(task.projectId, task.id, newStatus.raw)
            load(isRefresh = true)
        }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        if (isRefresh) {
            _state.update { it.copy(refreshing = true) }
        } else {
            _state.update { it.copy(loading = it.groups.isEmpty()) }
        }
        loadJob = viewModelScope.launch {
            repository.myTasks().collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.update {
                        val groups = resource.data?.let { data -> groupTasks(data) } ?: it.groups
                        it.copy(
                            loading = it.groups.isEmpty() && resource.data == null,
                            groups = groups,
                            isEmpty = groups.isEmpty(),
                        )
                    }
                    is Resource.Success -> _state.update {
                        val groups = groupTasks(resource.data)
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            groups = groups,
                            isEmpty = groups.isEmpty(),
                        )
                    }
                    is Resource.Error -> _state.update {
                        val groups = resource.data?.let { data -> groupTasks(data) } ?: it.groups
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = resource.error.message,
                            groups = groups,
                            isEmpty = groups.isEmpty(),
                        )
                    }
                }
            }
        }
    }

    private fun groupTasks(tasks: List<Task>): List<TaskGroup> {
        val overdue = mutableListOf<Task>()
        val dueToday = mutableListOf<Task>()
        val upcoming = mutableListOf<Task>()
        val later = mutableListOf<Task>()
        val noDueDate = mutableListOf<Task>()

        for (task in tasks) {
            val days = DateUtils.daysUntil(task.dueDate)
            when {
                days == null -> noDueDate.add(task)
                days < 0 -> overdue.add(task)
                days == 0L -> dueToday.add(task)
                days in 1..7 -> upcoming.add(task)
                else -> later.add(task)
            }
        }

        return listOf(
            TaskGroup("Overdue", overdue),
            TaskGroup("Due Today", dueToday),
            TaskGroup("Upcoming (7 days)", upcoming),
            TaskGroup("Later", later),
            TaskGroup("No Due Date", noDueDate),
        ).filter { it.tasks.isNotEmpty() }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _state.update { it.copy(offline = !online) }
            }
        }
    }
}
