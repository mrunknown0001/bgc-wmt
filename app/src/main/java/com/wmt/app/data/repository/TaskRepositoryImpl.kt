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
import com.wmt.app.domain.model.Comment
import com.wmt.app.domain.model.Task
import com.wmt.app.domain.model.TaskDetail
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.util.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import com.wmt.app.util.ImageTranscoder
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
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
        val bodyPart = body.toRequestBody("text/plain".toMediaTypeOrNull())
        val parts = attachmentUris.mapNotNull { buildFilePart(it) }
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

    /**
     * Wraps a content URI in a multipart part named `attachments[]` (Laravel array field).
     * Gallery images the backend would reject (HEIC/HEIF, oversized) are transcoded to
     * JPEG first; everything else streams straight from the ContentResolver so large
     * files (videos can be up to 50MB) never sit fully in memory.
     */
    private fun buildFilePart(uriString: String): MultipartBody.Part? {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = displayName(uri) ?: "attachment"
        // Probe once so unreadable URIs are skipped instead of failing mid-request.
        resolver.openInputStream(uri)?.close() ?: return null

        ImageTranscoder.transcodeIfNeeded(context, uri, mime, fileSize(uri))?.let { jpeg ->
            val jpegName = name.substringBeforeLast('.') + ".jpg"
            return MultipartBody.Part.createFormData(
                "attachments[]",
                jpegName,
                jpeg.asRequestBody("image/jpeg".toMediaTypeOrNull()),
            )
        }

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
            override fun contentLength(): Long = fileSize(uri) ?: -1L
            // One-shot keeps HttpLoggingInterceptor from buffering the whole file to log it.
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.source()?.use { sink.writeAll(it) }
            }
        }
        return MultipartBody.Part.createFormData("attachments[]", name, requestBody)
    }

    private fun fileSize(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
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
