package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.local.db.dao.TaskDao
import com.wmt.app.data.local.db.toDomain
import com.wmt.app.data.local.db.toEntity
import com.wmt.app.data.remote.AttachmentPartFactory
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.CreateTaskRequest
import com.wmt.app.data.remote.dto.PatchRequest
import com.wmt.app.data.remote.dto.UpdateTaskRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.Comment
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val attachments: AttachmentPartFactory,
    private val api: WmtApi,
    private val dao: TaskDao,
    private val moshi: Moshi,
) : TaskRepository {

    override fun myTasks(): Flow<Resource<List<Task>>> = flow {
        val cached = dao.observeAll().first().map { it.toDomain() }
        emit(Resource.Loading(cached))

        when (val result = safeApiCall(moshi) { api.myTasks() }) {
            is Resource.Success -> {
                val tasks = result.data.taskGroups.all().map { it.toDomain() }
                dao.replaceAll(tasks.map { it.toEntity() })
                emit(Resource.Success(tasks))
            }
            is Resource.Error -> emit(Resource.Error(result.error, cached))
            is Resource.Loading -> Unit
        }
    }

    override suspend fun taskDetail(projectId: Int, taskId: Int): Resource<TaskDetail> =
        safeApiCall(moshi) {
            val response = if (isPersonal(projectId)) {
                api.personalTaskDetail(taskId)
            } else {
                api.taskDetail(projectId, taskId)
            }
            response.toDomain()
        }

    override suspend fun updateStatus(projectId: Int, taskId: Int, status: String): Resource<Task> =
        safeApiCall(moshi) {
            val patch = PatchRequest(field = "status", value = status)
            val response = if (isPersonal(projectId)) {
                api.patchPersonalTask(taskId, patch)
            } else {
                api.patchTask(projectId, taskId, patch)
            }
            response.task.toDomain()
        }

    override suspend fun addComment(
        projectId: Int,
        taskId: Int,
        body: String,
        attachmentUris: List<String>,
    ): Resource<Unit> = safeApiCall(moshi) {
        val bodyPart = AttachmentPartFactory.textPart(body)
        val parts = attachments.fileParts(attachmentUris)
        if (isPersonal(projectId)) {
            api.addPersonalTaskComment(taskId, bodyPart, parts)
        } else {
            api.addComment(projectId, taskId, bodyPart, parts)
        }
        Unit
    }

    override suspend fun updateTask(
        projectId: Int,
        taskId: Int,
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        dueDate: String?,
        startDate: String?,
        isRecurring: Boolean,
        recurrenceFrequency: String?,
        recurrenceInterval: Int?,
        collaboratorIds: List<Int>?,
    ): Resource<Task> = safeApiCall(moshi) {
        val request = UpdateTaskRequest(
            title = title,
            status = status,
            priority = priority,
            description = description,
            assignedTo = assignedTo,
            dueDate = dueDate,
            startDate = startDate,
            isRecurring = isRecurring,
            recurrenceFrequency = recurrenceFrequency,
            recurrenceInterval = recurrenceInterval,
            collaboratorIds = collaboratorIds,
        )
        val response = if (isPersonal(projectId)) {
            api.updatePersonalTask(taskId, request)
        } else {
            api.updateTask(projectId, taskId, request)
        }
        response.task.toDomain()
    }

    override suspend fun deleteTask(projectId: Int, taskId: Int): Resource<Unit> =
        safeApiCall(moshi) {
            if (isPersonal(projectId)) api.deletePersonalTask(taskId) else api.deleteTask(projectId, taskId)
        }

    override suspend fun createTask(
        projectId: Int,
        title: String,
        status: String,
        priority: String,
        description: String?,
        assignedTo: Int?,
        sectionId: Int?,
        dueDate: String?,
        isRecurring: Boolean,
        recurrenceFrequency: String?,
        recurrenceInterval: Int?,
        collaboratorIds: List<Int>?,
    ): Resource<Task> = safeApiCall(moshi) {
        val request = CreateTaskRequest(
            title = title,
            status = status,
            priority = priority,
            description = description,
            assignedTo = assignedTo,
            sectionId = sectionId,
            dueDate = dueDate,
            isRecurring = isRecurring,
            recurrenceFrequency = recurrenceFrequency,
            recurrenceInterval = recurrenceInterval,
            collaboratorIds = collaboratorIds,
        )
        val response = if (isPersonal(projectId)) {
            api.createPersonalTask(request)
        } else {
            api.createTask(projectId, request)
        }
        response.task.toDomain()
    }

    override suspend fun calendarTasks(month: String): Resource<List<Task>> =
        safeApiCall(moshi) { api.calendar(month).tasks.map { it.toDomain() } }

    override suspend fun olderComments(
        projectId: Int,
        taskId: Int,
        beforeId: Int,
    ): Resource<Pair<List<Comment>, Boolean>> = safeApiCall(moshi) {
        val response = if (isPersonal(projectId)) {
            api.olderPersonalComments(taskId, beforeId)
        } else {
            api.olderComments(projectId, taskId, beforeId)
        }
        response.comments.map { it.toDomain() } to response.hasMore
    }

    private companion object {
        /**
         * Sentinel project id for personal (project-less) tasks. The server serializes
         * them with `project_id: null`, which the DTO mapper coalesces to 0; all task
         * operations for id 0 go through the standalone `/api/tasks/...` routes.
         */
        fun isPersonal(projectId: Int) = projectId <= 0
    }
}
