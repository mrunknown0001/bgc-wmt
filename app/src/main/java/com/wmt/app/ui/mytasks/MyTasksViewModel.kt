package com.wmt.app.ui.mytasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.data.remote.RealtimeClient
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskPriority
import com.wmt.app.fcm.InAppNotificationBus
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

data class MyTasksStats(
    val open: Int = 0,
    val overdue: Int = 0,
    val dueToday: Int = 0,
)

data class MyTasksUiState(
    val loading: Boolean = true,
    /** Unfiltered tasks — the calendar view uses these directly. */
    val allTasks: List<Task> = emptyList(),
    /** Filtered + grouped tasks for the list view. */
    val groups: List<TaskGroup> = emptyList(),
    val stats: MyTasksStats = MyTasksStats(),
    val error: String? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val isEmpty: Boolean = false,
    val statusFilter: TaskStatus? = null,
    val priorityFilter: TaskPriority? = null,
    val searchQuery: String = "",
    val creating: Boolean = false,
    val createError: String? = null,
    /** Month tasks from /api/calendar (assigned + collaborating); null until fetched. */
    val calendarTasks: List<Task>? = null,
    /** One-shot message for a rejected inline action; shown once, then cleared. */
    val actionMessage: String? = null,
    /** Increments each time the user completes a task — drives the confetti burst. */
    val celebrations: Int = 0,
) {
    val hasActiveFilters: Boolean
        get() = statusFilter != null || priorityFilter != null || searchQuery.isNotBlank()
}

@HiltViewModel
class MyTasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val networkMonitor: NetworkMonitor,
    private val realtimeClient: RealtimeClient,
    private val inAppBus: InAppNotificationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(MyTasksUiState())
    val state: StateFlow<MyTasksUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(isRefresh = false)
        observeConnectivity()
        // Silent live refresh: notification events (assignment, comments, due dates)
        // signal that the visible task list may be stale.
        viewModelScope.launch { realtimeClient.inboxEvents.collect { load(isRefresh = false) } }
        viewModelScope.launch { inAppBus.messages.collect { load(isRefresh = false) } }
    }

    fun refresh() = load(isRefresh = true)

    /**
     * Silently refetches when the screen re-enters composition (back-nav from task
     * detail, tab switch) so completed/edited tasks don't linger stale. The first
     * call (initial composition, right after init's load) is skipped.
     */
    fun onScreenVisible() {
        if (firstVisible) {
            firstVisible = false
            return
        }
        load(isRefresh = false)
    }

    private var firstVisible = true

    fun setStatusFilter(status: TaskStatus?) = applyFilters { it.copy(statusFilter = status) }

    fun setPriorityFilter(priority: TaskPriority?) = applyFilters { it.copy(priorityFilter = priority) }

    fun setSearchQuery(query: String) = applyFilters { it.copy(searchQuery = query) }

    fun clearFilters() = applyFilters {
        it.copy(statusFilter = null, priorityFilter = null, searchQuery = "")
    }

    fun clearCreateError() = _state.update { it.copy(createError = null) }

    /**
     * Fetches the calendar feed for [month] ("YYYY-MM") — includes tasks the user
     * collaborates on, which /api/my-tasks doesn't. Falls back silently: on failure
     * the calendar keeps rendering the assigned tasks it already has.
     */
    fun loadCalendarMonth(month: String) {
        calendarJob?.cancel()
        calendarJob = viewModelScope.launch {
            val result = repository.calendarTasks(month)
            if (result is Resource.Success) {
                _state.update { it.copy(calendarTasks = result.data) }
            }
        }
    }

    private var calendarJob: Job? = null

    /** Creates a personal (project-less) task via the standalone /api/tasks route. */
    fun createPersonalTask(
        title: String,
        status: String,
        priority: String,
        description: String?,
        dueDate: String?,
        recurrenceFrequency: String?,
        recurrenceInterval: Int?,
        onSuccess: () -> Unit,
    ) {
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            when (
                val result = repository.createTask(
                    projectId = 0,
                    title = title,
                    status = status,
                    priority = priority,
                    description = description,
                    assignedTo = null,
                    sectionId = null,
                    dueDate = dueDate,
                    isRecurring = recurrenceFrequency != null,
                    recurrenceFrequency = recurrenceFrequency,
                    recurrenceInterval = if (recurrenceFrequency != null) recurrenceInterval ?: 1 else null,
                )
            ) {
                is Resource.Success -> {
                    _state.update { it.copy(creating = false) }
                    load(isRefresh = true)
                    onSuccess()
                }
                is Resource.Error -> _state.update {
                    it.copy(creating = false, createError = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * Tap-to-complete from the list: flip the row at once so the list feels instant, then
     * reconcile with the server. A rejected change (the API blocks some status moves, e.g.
     * an unfinished dependency, and explains itself in the 422) is put back and reported in
     * the server's own words — and the confetti waits until the write actually landed.
     */
    fun toggleComplete(task: Task) {
        val newStatus = if (task.statusEnum == TaskStatus.DONE) TaskStatus.TO_DO else TaskStatus.DONE
        val previousStatus = task.status
        _state.update { st ->
            val flipped = st.allTasks.map {
                if (it.id == task.id) it.copy(status = newStatus.raw) else it
            }
            st.withTasks(flipped)
        }
        viewModelScope.launch {
            when (val result = repository.updateStatus(task.projectId, task.id, newStatus.raw)) {
                is Resource.Success -> {
                    if (newStatus == TaskStatus.DONE) {
                        _state.update { it.copy(celebrations = it.celebrations + 1) }
                    }
                    load(isRefresh = true)
                }
                is Resource.Error -> _state.update { st ->
                    val reverted = st.allTasks.map {
                        if (it.id == task.id) it.copy(status = previousStatus) else it
                    }
                    st.withTasks(reverted).copy(actionMessage = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun clearActionMessage() = _state.update { it.copy(actionMessage = null) }

    private fun applyFilters(mutate: (MyTasksUiState) -> MyTasksUiState) {
        _state.update { mutate(it).let { st -> st.withTasks(st.allTasks) } }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        if (isRefresh) {
            _state.update { it.copy(refreshing = true) }
        } else {
            _state.update { it.copy(loading = it.allTasks.isEmpty()) }
        }
        loadJob = viewModelScope.launch {
            repository.myTasks().collect { resource ->
                when (resource) {
                    is Resource.Loading -> _state.update {
                        val tasks = resource.data ?: it.allTasks
                        it.withTasks(tasks).copy(
                            loading = it.allTasks.isEmpty() && resource.data == null,
                        )
                    }
                    is Resource.Success -> _state.update {
                        it.withTasks(resource.data).copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                    is Resource.Error -> _state.update {
                        val tasks = resource.data ?: it.allTasks
                        it.withTasks(tasks).copy(
                            loading = false,
                            refreshing = false,
                            error = resource.error.message,
                        )
                    }
                }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _state.update { it.copy(offline = !online) }
            }
        }
    }
}

/** Recomputes the derived groups + stats for a new task list under the current filters. */
private fun MyTasksUiState.withTasks(tasks: List<Task>): MyTasksUiState {
    val filtered = tasks.filter { task ->
        (statusFilter == null || task.statusEnum == statusFilter) &&
            (priorityFilter == null || task.priorityEnum == priorityFilter) &&
            (searchQuery.isBlank() || task.title.contains(searchQuery.trim(), ignoreCase = true))
    }
    val open = tasks.filter { it.statusEnum != TaskStatus.DONE && it.statusEnum != TaskStatus.CANCELLED }
    val groups = groupByDueDate(filtered)
    return copy(
        allTasks = tasks,
        groups = groups,
        isEmpty = groups.isEmpty(),
        stats = MyTasksStats(
            open = open.size,
            overdue = open.count { (DateUtils.daysUntil(it.dueDate) ?: 0L) < 0L },
            dueToday = open.count { DateUtils.daysUntil(it.dueDate) == 0L },
        ),
    )
}

private fun groupByDueDate(tasks: List<Task>): List<TaskGroup> {
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
