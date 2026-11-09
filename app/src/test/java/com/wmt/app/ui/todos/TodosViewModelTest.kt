package com.wmt.app.ui.todos

import com.wmt.app.domain.model.PersonalTodo
import com.wmt.app.domain.repository.TodoRepository
import com.wmt.app.testing.MainDispatcherRule
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Every to-do change is applied on screen before the server has answered. When the server
 * refuses, the rows come back -- but until this was fixed nothing was said, and the
 * confirmation for the change that never happened stayed on screen.
 *
 * Found on a device: "Clear all" announced "All to-dos cleared", the DELETE came back 404,
 * and the three to-dos quietly reappeared with no message at all.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rows = listOf(
        PersonalTodo(id = 1, title = "Alpha", isCompleted = false, position = 0),
        PersonalTodo(id = 2, title = "Bravo", isCompleted = false, position = 1),
    )

    private class FakeTodos(
        val stored: List<PersonalTodo>,
        var writeResult: Resource<Unit>,
    ) : TodoRepository {
        override suspend fun todos(): Resource<List<PersonalTodo>> = Resource.Success(stored)
        override suspend fun addTodo(title: String): Resource<PersonalTodo> = error("not used")
        override suspend fun setCompleted(id: Int, completed: Boolean): Resource<PersonalTodo> =
            when (val r = writeResult) {
                is Resource.Error -> Resource.Error(r.error)
                else -> Resource.Success(stored.first { it.id == id }.copy(isCompleted = completed))
            }
        override suspend fun renameTodo(id: Int, title: String): Resource<PersonalTodo> = error("not used")
        override suspend fun deleteTodo(id: Int): Resource<Unit> = writeResult
        override suspend fun clearCompleted(): Resource<Unit> = writeResult
        override suspend fun clearAll(): Resource<Unit> = writeResult
        override suspend fun reorder(ids: List<Int>): Resource<Unit> = writeResult
    }

    private fun viewModel(writeResult: Resource<Unit>) =
        TodosViewModel(FakeTodos(rows, writeResult))

    @Test
    fun `a refused clear all puts the rows back and says why`() = runTest {
        val refusal: Resource<Unit> = Resource.Error(AppError.Forbidden("These to-dos are locked."))
        val vm = viewModel(refusal)
        advanceUntilIdle()

        vm.clearAll()
        advanceUntilIdle()

        assertEquals("the server's rows come back", rows, vm.state.value.todos)
        assertEquals("the server's own words are shown", "These to-dos are locked.", vm.state.value.error)
        assertNull("no confirmation for a change that never happened", vm.state.value.message)
    }

    @Test
    fun `a refusal with no message still says something`() = runTest {
        // A 404 from a route the server has not deployed carries no useful words of its own.
        val vm = viewModel(Resource.Error(AppError.Forbidden("")))
        advanceUntilIdle()

        vm.clearAll()
        advanceUntilIdle()

        assertEquals("The to-dos were not cleared.", vm.state.value.error)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `a successful clear all keeps its confirmation and says nothing is wrong`() = runTest {
        val vm = viewModel(Resource.Success(Unit))
        advanceUntilIdle()

        vm.clearAll()
        advanceUntilIdle()

        assertEquals("All to-dos cleared", vm.state.value.message)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a refused delete puts the row back and says why`() = runTest {
        val vm = viewModel(Resource.Error(AppError.Forbidden("Not yours to remove.")))
        advanceUntilIdle()

        vm.delete(rows.first())
        advanceUntilIdle()

        assertEquals(rows, vm.state.value.todos)
        assertEquals("Not yours to remove.", vm.state.value.error)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `a refused reorder puts the server's order back and says why`() = runTest {
        val vm = viewModel(Resource.Error(AppError.Forbidden("Order not saved.")))
        advanceUntilIdle()

        vm.move(rows[1], by = -1)
        advanceUntilIdle()

        assertEquals(rows.map { it.id }, vm.state.value.todos.map { it.id })
        assertEquals("Order not saved.", vm.state.value.error)
    }

    @Test
    fun `a refused tick puts the box back and says why`() = runTest {
        val vm = viewModel(Resource.Error(AppError.Forbidden("That to-do is locked.")))
        advanceUntilIdle()

        vm.toggle(rows.first())
        advanceUntilIdle()

        assertEquals(false, vm.state.value.todos.first { it.id == 1 }.isCompleted)
        assertEquals("That to-do is locked.", vm.state.value.error)
        assertNull(vm.state.value.message)
    }
}
