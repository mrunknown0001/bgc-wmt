package com.wmt.app.data.remote.api

import com.wmt.app.data.remote.dto.ApiList
import com.wmt.app.data.remote.dto.ChangePasswordRequest
import com.wmt.app.data.remote.dto.CreateProjectRequest
import com.wmt.app.data.remote.dto.CreateTaskRequest
import com.wmt.app.data.remote.dto.DashboardDto
import com.wmt.app.data.remote.dto.DeviceTokenRequest
import com.wmt.app.data.remote.dto.LoginRequest
import com.wmt.app.data.remote.dto.LoginResponse
import com.wmt.app.data.remote.dto.LogoutOtherDevicesRequest
import com.wmt.app.data.remote.dto.MessageResponse
import com.wmt.app.data.remote.dto.NotificationDto
import com.wmt.app.data.remote.dto.NotificationPreferenceRequest
import com.wmt.app.data.remote.dto.PatchRequest
import com.wmt.app.data.remote.dto.MyTasksResponse
import com.wmt.app.data.remote.dto.ProjectDetailResponse
import com.wmt.app.data.remote.dto.ProjectDto
import com.wmt.app.data.remote.dto.ProjectResponse
import com.wmt.app.data.remote.dto.TaskDetailResponse
import com.wmt.app.data.remote.dto.TaskDto
import com.wmt.app.data.remote.dto.TaskPatchResponse
import com.wmt.app.data.remote.dto.TaskResponse
import com.wmt.app.data.remote.dto.UpdateTaskRequest
import com.wmt.app.data.remote.dto.UnreadCountResponse
import com.wmt.app.data.remote.dto.UserDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface WmtApi {

    /** Reachability probe used by the Server Setup screen. Passes an absolute URL. */
    @GET
    suspend fun health(@Url url: String): Response<Unit>

    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/logout")
    suspend fun logout(): MessageResponse

    @GET("api/user")
    suspend fun currentUser(): UserDto

    @PUT("api/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): MessageResponse

    @POST("api/logout-other-devices")
    suspend fun logoutOtherDevices(@Body body: LogoutOtherDevicesRequest): MessageResponse

    // Distinct path from the web SPA's /api/device-tokens (which sits behind web CSRF
    // middleware and rejects Bearer-only requests with 419). See routes/api.php.
    @POST("api/mobile/device-tokens")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): MessageResponse

    @GET("api/dashboard")
    suspend fun dashboard(): DashboardDto

    @GET("api/my-tasks")
    suspend fun myTasks(): MyTasksResponse

    @GET("api/projects")
    suspend fun projects(@Query("search") search: String? = null): ApiList<ProjectDto>

    @POST("api/projects")
    suspend fun createProject(@Body body: CreateProjectRequest): ProjectResponse

    @PUT("api/projects/{id}")
    suspend fun updateProject(@Path("id") id: Int, @Body body: CreateProjectRequest): ProjectResponse

    @DELETE("api/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Int)

    @GET("api/projects/{id}")
    suspend fun projectDetail(@Path("id") id: Int): ProjectDetailResponse

    @POST("api/projects/{projectId}/tasks")
    suspend fun createTask(
        @Path("projectId") projectId: Int,
        @Body body: CreateTaskRequest,
    ): TaskResponse

    @PUT("api/projects/{projectId}/tasks/{taskId}")
    suspend fun updateTask(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Body body: UpdateTaskRequest,
    ): TaskResponse

    @GET("api/projects/{projectId}/tasks/{taskId}")
    suspend fun taskDetail(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
    ): TaskDetailResponse

    @DELETE("api/projects/{projectId}/tasks/{taskId}")
    suspend fun deleteTask(@Path("projectId") projectId: Int, @Path("taskId") taskId: Int)

    @PATCH("api/projects/{projectId}/tasks/{taskId}/patch")
    suspend fun patchTask(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Body body: PatchRequest,
    ): TaskPatchResponse

    @Multipart
    @POST("api/projects/{projectId}/tasks/{taskId}/comments")
    suspend fun addComment(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Part("body") body: RequestBody,
        @Part attachments: List<MultipartBody.Part>,
    ): MessageResponse

    @GET("api/notifications")
    suspend fun notifications(): ApiList<NotificationDto>

    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): UnreadCountResponse

    @PATCH("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): MessageResponse

    @POST("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): MessageResponse

    @GET("api/notification-preferences")
    suspend fun notificationPreferences(): Map<String, Boolean>

    @POST("api/notification-preferences")
    suspend fun updateNotificationPreference(
        @Body body: NotificationPreferenceRequest,
    ): MessageResponse
}
