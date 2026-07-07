package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.CreateTodoRequest
import com.wmt.app.data.remote.dto.UpdateTodoRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.PersonalTodo
import com.wmt.app.domain.repository.TodoRepository
import com.wmt.app.util.Resource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val moshi: Moshi,
) : TodoRepository {

    override suspend fun todos(): Resource<List<PersonalTodo>> =
        safeApiCall(moshi) { api.personalTodos().todos.map { it.toDomain() } }

    override suspend fun addTodo(title: String): Resource<PersonalTodo> =
        safeApiCall(moshi) { api.createPersonalTodo(CreateTodoRequest(title)).todo.toDomain() }

    override suspend fun setCompleted(id: Int, completed: Boolean): Resource<PersonalTodo> =
        safeApiCall(moshi) {
            api.updatePersonalTodo(id, UpdateTodoRequest(isCompleted = completed)).todo.toDomain()
        }

    override suspend fun renameTodo(id: Int, title: String): Resource<PersonalTodo> =
        safeApiCall(moshi) {
            api.updatePersonalTodo(id, UpdateTodoRequest(title = title)).todo.toDomain()
        }

    override suspend fun deleteTodo(id: Int): Resource<Unit> =
        safeApiCall(moshi) {
            api.deletePersonalTodo(id)
            Unit
        }

    override suspend fun clearCompleted(): Resource<Unit> =
        safeApiCall(moshi) {
            api.clearCompletedTodos()
            Unit
        }
}
