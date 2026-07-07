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
                message = if (completed) "To-do completed 🎉" else "To-do reopened",
            )
        }
        viewModelScope.launch {
            if (repository.setCompleted(todo.id, completed) is Resource.Error) load()
        }
    }

    fun delete(todo: PersonalTodo) {
        _state.update { st ->
            st.copy(todos = st.todos.filterNot { it.id == todo.id }, message = "To-do removed")
        }
        viewModelScope.launch {
            if (repository.deleteTodo(todo.id) is Resource.Error) load()
        }
    }

    fun clearCompleted() {
        _state.update { st ->
            st.copy(
                todos = st.todos.filterNot { it.isCompleted },
                message = "Completed to-dos cleared",
            )
        }
        viewModelScope.launch {
            if (repository.clearCompleted() is Resource.Error) load()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private companion object {
        /** Open items first (by position), completed at the bottom. */
        val todoOrder = compareBy<PersonalTodo>({ it.isCompleted }, { it.position })
    }
}
