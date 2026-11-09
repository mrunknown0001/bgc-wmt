package com.wmt.app.ui.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.PersonalTodo
import com.wmt.app.domain.repository.TodoRepository
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodosUiState(
    val loading: Boolean = true,
    val todos: List<PersonalTodo> = emptyList(),
    val error: String? = null,
    val adding: Boolean = false,
    /** Transient confirmation shown as a toast ("To-do added", …). */
    val message: String? = null,
)

@HiltViewModel
class TodosViewModel @Inject constructor(
    private val repository: TodoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TodosUiState())
    val state: StateFlow<TodosUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.todos.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val result = repository.todos()) {
                is Resource.Success -> _state.update {
                    it.copy(loading = false, todos = result.data.sortedWith(todoOrder))
                }
                is Resource.Error -> _state.update {
                    it.copy(loading = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun add(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(adding = true) }
        viewModelScope.launch {
            when (val result = repository.addTodo(trimmed)) {
                is Resource.Success -> _state.update {
                    it.copy(
                        adding = false,
                        todos = (it.todos + result.data).sortedWith(todoOrder),
                        message = "To-do added",
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(adding = false, error = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun toggle(todo: PersonalTodo) {
        val completed = !todo.isCompleted
        // Optimistic flip; reconcile with the server response.
        _state.update { st ->
            st.copy(
                todos = st.todos
                    .map { if (it.id == todo.id) it.copy(isCompleted = completed) else it }
                    .sortedWith(todoOrder),
            )
        }
        viewModelScope.launch {
            when (val result = repository.setCompleted(todo.id, completed)) {
                is Resource.Error -> revert(result.error.message, "That to-do did not change.")
                else -> announce(if (completed) "To-do completed 🎉" else "To-do reopened")
            }
        }
    }

    fun delete(todo: PersonalTodo) {
        _state.update { st -> st.copy(todos = st.todos.filterNot { it.id == todo.id }) }
        viewModelScope.launch {
            when (val result = repository.deleteTodo(todo.id)) {
                is Resource.Error -> revert(result.error.message, "That to-do was not removed.")
                else -> announce("To-do removed")
            }
        }
    }

    fun clearCompleted() {
        _state.update { st -> st.copy(todos = st.todos.filterNot { it.isCompleted }) }
        viewModelScope.launch {
            when (val result = repository.clearCompleted()) {
                is Resource.Error -> revert(result.error.message, "The completed to-dos were not cleared.")
                else -> announce("Completed to-dos cleared")
            }
        }
    }

    /** Everything, done or not. The screen asks first; this does not. */
    fun clearAll() {
        _state.update { it.copy(todos = emptyList()) }
        viewModelScope.launch {
            when (val result = repository.clearAll()) {
                is Resource.Error -> revert(result.error.message, "The to-dos were not cleared.")
                else -> announce("All to-dos cleared")
            }
        }
    }

    /** Moves an open to-do one place up (-1) or down (+1), and saves the new order. */
    fun move(todo: PersonalTodo, by: Int) {
        val before = _state.value.todos
        val after = TodoOrder.move(before, todo.id, by)
        if (after.map { it.id } == before.map { it.id }) return
        _state.update { it.copy(todos = after) }
        viewModelScope.launch {
            // A failed save puts the server's order back rather than leaving a lie on screen.
            val result = repository.reorder(after.map { it.id })
            if (result is Resource.Error) revert(result.error.message, "That new order did not save.")
        }
    }

    /**
     * The rows move as soon as they are touched, but nothing is *claimed* until the server
     * has agreed. Announcing up front told people "All to-dos cleared" over a list that was
     * about to reappear underneath the words.
     */
    private fun announce(message: String) = _state.update { it.copy(message = message) }

    /**
     * A refused change puts the server's rows back and says why. Without this the rows
     * simply returned with nothing said, and the screen looked broken.
     *
     * [load] clears `error` as it starts, so the message is set after it, not before.
     */
    private fun revert(serverMessage: String?, fallback: String) {
        load()
        _state.update {
            it.copy(message = null, error = serverMessage?.takeIf(String::isNotBlank) ?: fallback)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private companion object {
        /** Open items first (by position), completed at the bottom. */
        val todoOrder = TodoOrder.comparator
    }
}
