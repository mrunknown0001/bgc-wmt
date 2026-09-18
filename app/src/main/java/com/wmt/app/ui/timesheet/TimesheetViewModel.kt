package com.wmt.app.ui.timesheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.TaskTimesheet
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

data class TimesheetUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** An action is in flight; the buttons wait rather than queueing presses. */
    val busy: Boolean = false,
    val sheet: TaskTimesheet = TaskTimesheet(),
    val error: String? = null,
    /** One-shot message, usually the server's own account of what happened. */
    val message: String? = null,
    /** Field errors from a 422, so a rejected duration is shown against the box. */
    val fieldErrors: Map<String, List<String>> = emptyMap(),
)

/**
 * A task's timesheet and the corrections raised against it.
 *
 * Nothing here edits a figure directly. Effort is normally the clock's own work, so a
 * change is a request that somebody decides — and when the person asking happens to be
 * that somebody, the server applies it at once and says so.
 */
@HiltViewModel
class TimesheetViewModel @Inject constructor(
    private val repository: TaskRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val taskId: Int = checkNotNull(savedStateHandle["taskId"])

    private val _state = MutableStateFlow(TimesheetUiState())
    val state: StateFlow<TimesheetUiState> = _state.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun retry() = load(isRefresh = false)

    fun dismissMessage() = _state.update { it.copy(message = null, fieldErrors = emptyMap()) }

    /** Removes an entry somebody typed. The clock's own are refused with an explanation. */
    fun deleteLog(timeLogId: Int) = runAction {
        when (val result = repository.deleteTimeLog(timeLogId)) {
            is Resource.Success -> Outcome(message = "Entry removed.")
            is Resource.Error -> Outcome(error = result.error)
            is Resource.Loading -> null
        }
    }

    fun amend(timeLogId: Int, duration: String, reason: String) = runAction {
        when (val result = repository.amendTimeLog(timeLogId, duration, reason)) {
            is Resource.Success -> Outcome(
                // The server decides whether this took effect or is waiting: the person
                // asking may be the one who signs it off.
                message = if (result.data.applied) {
                    "Entry updated."
                } else {
                    "Correction sent for a decision."
                },
            )
            is Resource.Error -> Outcome(error = result.error)
            is Resource.Loading -> null
        }
    }

    fun addEntry(duration: String, loggedOn: String, reason: String) = runAction {
        when (val result = repository.addTimeLogEntry(taskId, duration, loggedOn, reason)) {
            is Resource.Success -> Outcome(
                message = if (result.data.applied) "Entry added." else "Request sent for a decision.",
            )
            is Resource.Error -> Outcome(error = result.error)
            is Resource.Loading -> null
        }
    }

    fun decide(amendmentId: Int, approve: Boolean, note: String? = null) = runAction {
        when (val result = repository.decideAmendment(amendmentId, approve, note)) {
            is Resource.Success -> Outcome(
                message = if (approve) "Correction approved." else "Correction rejected.",
            )
            is Resource.Error -> Outcome(error = result.error)
            is Resource.Loading -> null
        }
    }

    private class Outcome(val message: String? = null, val error: AppError? = null)

    /**
     * Runs an action, then re-reads the sheet.
     *
     * Every one of these changes more than it reports: a decision moves a figure and the
     * task total, and a deletion changes what the clock is allowed to infer about the
     * rest of that day. Re-reading is cheaper than trying to mirror that here.
     */
    private fun runAction(action: suspend () -> Outcome?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val outcome = action()
            val error = outcome?.error
            _state.update {
                it.copy(
                    busy = false,
                    message = error?.message ?: outcome?.message,
                    fieldErrors = (error as? AppError.Validation)?.fieldErrors.orEmpty(),
                )
            }
            if (error == null) load(isRefresh = true)
        }
    }

    private fun load(isRefresh: Boolean) {
        _state.update {
            if (isRefresh) it.copy(refreshing = true) else it.copy(loading = it.sheet.isEmpty)
        }
        viewModelScope.launch {
            when (val result = repository.timesheet(taskId)) {
                is Resource.Success -> _state.update {
                    it.copy(loading = false, refreshing = false, sheet = result.data, error = null)
                }
                is Resource.Error -> _state.update {
                    // A failed refresh keeps the sheet on screen; a failed first read has
                    // nothing to keep.
                    if (it.sheet.isEmpty) {
                        it.copy(loading = false, refreshing = false, error = result.error.message)
                    } else {
                        it.copy(loading = false, refreshing = false, message = result.error.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
