package com.wmt.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.squareup.moshi.Moshi
import com.wmt.app.data.local.db.dao.TaskDao
import com.wmt.app.data.local.db.toDomain
import com.wmt.app.data.local.db.toEntity
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.CreateTaskRequest
import com.wmt.app.data.remote.dto.PatchRequest
import com.wmt.app.data.remote.dto.UpdateTaskRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.util.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
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
        safeApiCall(moshi) { api.taskDetail(projectId, taskId).toDomain() }

    override suspend fun updateStatus(projectId: Int, taskId: Int, status: String): Resource<Task> =
        safeApiCall(moshi) {
            api.patchTask(projectId, taskId, PatchRequest(field = "status", value = status))
                .task.toDomain()
        }

    override suspend fun addComment(
        projectId: Int,
        taskId: Int,
        body: String,
        attachmentUris: List<String>,
    ): Resource<Unit> = safeApiCall(moshi) {
        val bodyPart = body.toRequestBody("text/plain".toMediaTypeOrNull())
        val parts = attachmentUris.mapNotNull { buildFilePart(it) }
        api.addComment(projectId, taskId, bodyPart, parts)
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
    ): Resource<Task> = safeApiCall(moshi) {
        api.updateTask(
            projectId,
            taskId,
            UpdateTaskRequest(
                title = title,
                status = status,
                priority = priority,
                description = description,
                assignedTo = assignedTo,
                dueDate = dueDate,
                startDate = startDate,
            ),
        ).task.toDomain()
    }

    override suspend fun deleteTask(projectId: Int, taskId: Int): Resource<Unit> =
        safeApiCall(moshi) { api.deleteTask(projectId, taskId) }

    /** Reads a content URI into a multipart part named `attachments[]` (Laravel array field). */
    private fun buildFilePart(uriString: String): MultipartBody.Part? {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = displayName(uri) ?: "attachment"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("attachments[]", name, requestBody)
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
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
    ): Resource<Task> = safeApiCall(moshi) {
        api.createTask(
            projectId,
            CreateTaskRequest(
                title = title,
                status = status,
                priority = priority,
                description = description,
                assignedTo = assignedTo,
                sectionId = sectionId,
                dueDate = dueDate,
            ),
        ).task.toDomain()
    }
}
