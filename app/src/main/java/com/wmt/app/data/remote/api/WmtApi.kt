package com.wmt.app.data.remote.api

import com.wmt.app.data.remote.dto.ApiList
import com.wmt.app.data.remote.dto.ApprovalAdvanceRequest
import com.wmt.app.data.remote.dto.ApprovalAdvanceResponse
import com.wmt.app.data.remote.dto.ApprovalCommentDto
import com.wmt.app.data.remote.dto.ApprovalCommentResponse
import com.wmt.app.data.remote.dto.ApprovalCountsDto
import com.wmt.app.data.remote.dto.ApprovalItemDetailResponse
import com.wmt.app.data.remote.dto.ApprovalItemDto
import com.wmt.app.data.remote.dto.ApprovalItemResponse
import com.wmt.app.data.remote.dto.ApprovalProjectDto
import com.wmt.app.data.remote.dto.ApprovalProjectResponse
import com.wmt.app.data.remote.dto.ApprovalRequestFormResponse
import com.wmt.app.data.remote.dto.ApprovalTrailResponse
import com.wmt.app.data.remote.dto.AvailableApprovalProjectsResponse
import com.wmt.app.data.remote.dto.MyApprovalsResponse
import com.wmt.app.data.remote.dto.MyRequestsResponse
import com.wmt.app.data.remote.dto.Paginated
import com.wmt.app.data.remote.dto.UpdateApprovalItemRequest
import com.wmt.app.data.remote.dto.AppSettingsDto
import com.wmt.app.data.remote.dto.CalendarResponse
import com.wmt.app.data.remote.dto.ChangePasswordRequest
import com.wmt.app.data.remote.dto.CreateTodoRequest
import com.wmt.app.data.remote.dto.PaginatedCommentsResponse
import com.wmt.app.data.remote.dto.SearchResponse
import com.wmt.app.data.remote.dto.SuccessResponse
import com.wmt.app.data.remote.dto.TodoResponse
import com.wmt.app.data.remote.dto.TodosResponse
import com.wmt.app.data.remote.dto.UpdateTodoRequest
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
import com.wmt.app.data.remote.dto.PausePreviewDto
import com.wmt.app.data.remote.dto.PauseTaskRequest
import com.wmt.app.data.remote.dto.TaskClockDto
import com.wmt.app.data.remote.dto.MyTasksResponse
import com.wmt.app.data.remote.dto.ProjectDetailResponse
import com.wmt.app.data.remote.dto.ProjectDto
import com.wmt.app.data.remote.dto.AddTimeLogRequest
import com.wmt.app.data.remote.dto.AmendTimeLogRequest
import com.wmt.app.data.remote.dto.AmendmentResponse
import com.wmt.app.data.remote.dto.ReviewAmendmentRequest
import com.wmt.app.data.remote.dto.TaskDetailResponse
import com.wmt.app.data.remote.dto.NotesResponse
import com.wmt.app.data.remote.dto.NoteResponse
import com.wmt.app.data.remote.dto.NoteWriteRequest
import com.wmt.app.data.remote.dto.NoteSavedResponse
import com.wmt.app.data.remote.dto.MyPersonnelResponse
import com.wmt.app.data.remote.dto.PersonnelOverdueResponse
import com.wmt.app.data.remote.dto.SharedRequestsResponse
import com.wmt.app.data.remote.dto.DelegationsResponse
import com.wmt.app.data.remote.dto.CreateDelegationRequest
import com.wmt.app.data.remote.dto.CreateDelegationResponse
import com.wmt.app.data.remote.dto.RemoveDelegationResponse
import com.wmt.app.data.remote.dto.PeopleOptionsResponse
import com.wmt.app.data.remote.dto.TimeCorrectionsResponse
import com.wmt.app.data.remote.dto.PaginatedActivitiesResponse
import com.wmt.app.data.remote.dto.PreviousValueResponse
import com.wmt.app.data.remote.dto.CustomFieldValuesRequest
import com.wmt.app.data.remote.dto.CustomFieldValuesResponse
import com.wmt.app.data.remote.dto.TimeLogDeletedResponse
import com.wmt.app.data.remote.dto.TimeLogsResponse
import com.wmt.app.data.remote.dto.TaskDto
import com.wmt.app.data.remote.dto.TaskPatchResponse
import com.wmt.app.data.remote.dto.TaskResponse
import com.wmt.app.data.remote.dto.TokenRefreshResponse
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
import retrofit2.http.PartMap
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

    // Mints a replacement and deletes the token that authorised the call, so the new
    // one must be persisted before anything else fires a request.
    @POST("api/token/refresh")
    suspend fun refreshToken(): TokenRefreshResponse

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

    // There is no POST/PUT/DELETE for projects in the API, deliberately: creating and
    // setting up a project is web work. The app once declared all three, and each 404'd.

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

    /** Your own comment only; anyone else's is a 403. Answers 204. */
    @DELETE("api/projects/{projectId}/tasks/{taskId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Path("commentId") commentId: Int,
    )

    @DELETE("api/tasks/{taskId}/comments/{commentId}")
    suspend fun deletePersonalComment(@Path("taskId") taskId: Int, @Path("commentId") commentId: Int)

    @PATCH("api/projects/{projectId}/tasks/{taskId}/patch")
    suspend fun patchTask(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Body body: PatchRequest,
    ): TaskPatchResponse

    // ---- The timesheet. Effort is normally the clock's own work, so this is read-only
    // apart from removing an entry a person typed; changing a figure is a correction
    // somebody decides, below.

    @GET("api/tasks/{taskId}/time-logs")
    suspend fun taskTimeLogs(@Path("taskId") taskId: Int): TimeLogsResponse

    /** Refuses with 422 on an entry the clock wrote: those are corrected, not removed. */
    @DELETE("api/time-logs/{timeLogId}")
    suspend fun deleteTimeLog(@Path("timeLogId") timeLogId: Int): TimeLogDeletedResponse

    /** Asks for an existing entry to say something else. */
    @POST("api/time-logs/{timeLogId}/amendments")
    suspend fun amendTimeLog(
        @Path("timeLogId") timeLogId: Int,
        @Body body: AmendTimeLogRequest,
    ): AmendmentResponse

    // Task-scoped, not log-scoped: there is no entry to hang it off yet.
    @POST("api/tasks/{taskId}/time-log-amendments")
    suspend fun addTimeLogEntry(
        @Path("taskId") taskId: Int,
        @Body body: AddTimeLogRequest,
    ): AmendmentResponse

    @POST("api/time-log-amendments/{amendmentId}/approve")
    suspend fun approveAmendment(
        @Path("amendmentId") amendmentId: Int,
        @Body body: ReviewAmendmentRequest,
    ): AmendmentResponse

    @POST("api/time-log-amendments/{amendmentId}/reject")
    suspend fun rejectAmendment(
        @Path("amendmentId") amendmentId: Int,
        @Body body: ReviewAmendmentRequest,
    ): AmendmentResponse

    // ---- The task clock. PATCH, not POST: these move a task between states rather
    // than creating anything. Project-scoped only, because a standalone task has no
    // project to hold the setting or to decide a later correction.

    @PATCH("api/projects/{projectId}/tasks/{taskId}/start")
    suspend fun startTaskClock(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
    ): TaskClockDto

    /** What pausing now would record. Read before the dialog, never counted locally. */
    @GET("api/projects/{projectId}/tasks/{taskId}/pause-preview")
    suspend fun taskPausePreview(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
    ): PausePreviewDto

    @PATCH("api/projects/{projectId}/tasks/{taskId}/pause")
    suspend fun pauseTaskClock(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Body body: PauseTaskRequest,
    ): TaskClockDto

    @PATCH("api/projects/{projectId}/tasks/{taskId}/resume")
    suspend fun resumeTaskClock(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
    ): TaskClockDto

    @Multipart
    @POST("api/projects/{projectId}/tasks/{taskId}/comments")
    suspend fun addComment(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Part("body") body: RequestBody,
        @Part attachments: List<MultipartBody.Part>,
    ): MessageResponse

    // ---- Personal (project-less) tasks. Same shapes as the project-nested routes. ----

    @POST("api/tasks")
    suspend fun createPersonalTask(@Body body: CreateTaskRequest): TaskResponse

    @GET("api/tasks/{taskId}")
    suspend fun personalTaskDetail(@Path("taskId") taskId: Int): TaskDetailResponse

    @PUT("api/tasks/{taskId}")
    suspend fun updatePersonalTask(
        @Path("taskId") taskId: Int,
        @Body body: UpdateTaskRequest,
    ): TaskResponse

    @DELETE("api/tasks/{taskId}")
    suspend fun deletePersonalTask(@Path("taskId") taskId: Int)

    @PATCH("api/tasks/{taskId}/patch")
    suspend fun patchPersonalTask(
        @Path("taskId") taskId: Int,
        @Body body: PatchRequest,
    ): TaskPatchResponse

    @Multipart
    @POST("api/tasks/{taskId}/comments")
    suspend fun addPersonalTaskComment(
        @Path("taskId") taskId: Int,
        @Part("body") body: RequestBody,
        @Part attachments: List<MultipartBody.Part>,
    ): MessageResponse

    /**
     * Laravel's paginator, 20 per page — the inbox pages through it with [page]. The
     * envelope carries `current_page`/`last_page`, so the end of the list is the
     * server's answer rather than a guess from a short page.
     */
    @GET("api/notifications")
    suspend fun notifications(
        @Query("filter") filter: String? = null,
        @Query("page") page: Int? = null,
    ): Paginated<NotificationDto>

    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): UnreadCountResponse

    @PATCH("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): MessageResponse

    @POST("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): MessageResponse

    @PATCH("api/notifications/{id}/bookmark")
    suspend fun toggleNotificationBookmark(@Path("id") id: String): SuccessResponse

    @PATCH("api/notifications/{id}/archive")
    suspend fun archiveNotification(@Path("id") id: String): SuccessResponse

    @PATCH("api/notifications/{id}/unarchive")
    suspend fun unarchiveNotification(@Path("id") id: String): SuccessResponse

    /** Flat map of switch key to state, e.g. `{"email_task_assigned": true, …}`. */
    @GET("api/notification-preferences")
    suspend fun notificationPreferences(): Map<String, Boolean>

    @POST("api/notification-preferences")
    suspend fun updateNotificationPreference(
        @Body body: NotificationPreferenceRequest,
    ): MessageResponse

    @GET("api/mobile/search")
    suspend fun search(@Query("q") query: String): SearchResponse

    @GET("api/settings")
    suspend fun appSettings(): AppSettingsDto

    @GET("api/calendar")
    suspend fun calendar(@Query("month") month: String): CalendarResponse

    @GET("api/projects/{projectId}/tasks/{taskId}/comments")
    suspend fun olderComments(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Query("before_id") beforeId: Int,
        @Query("limit") limit: Int = 20,
    ): PaginatedCommentsResponse

    @GET("api/tasks/{taskId}/comments")
    suspend fun olderPersonalComments(
        @Path("taskId") taskId: Int,
        @Query("before_id") beforeId: Int,
        @Query("limit") limit: Int = 20,
    ): PaginatedCommentsResponse

    // The timeline pages by cursor, like comments: the oldest id already shown, newest first.

    @GET("api/projects/{projectId}/tasks/{taskId}/activities")
    suspend fun olderActivities(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Query("before_id") beforeId: Int,
        @Query("limit") limit: Int = 20,
    ): PaginatedActivitiesResponse

    @GET("api/tasks/{taskId}/activities")
    suspend fun olderPersonalActivities(
        @Path("taskId") taskId: Int,
        @Query("before_id") beforeId: Int,
        @Query("limit") limit: Int = 20,
    ): PaginatedActivitiesResponse

    /** The full text a custom field change replaced, for a row with has_previous_value. */
    @GET("api/task-activities/{activityId}/previous-value")
    suspend fun previousValue(@Path("activityId") activityId: Int): PreviousValueResponse

    /** Changed fields only; a locked field the viewer may not change is a 422 naming it. */
    @PUT("api/projects/{projectId}/tasks/{taskId}/custom-fields")
    suspend fun saveCustomFields(
        @Path("projectId") projectId: Int,
        @Path("taskId") taskId: Int,
        @Body body: CustomFieldValuesRequest,
    ): CustomFieldValuesResponse

    // ---- Personal to-dos ----

    @GET("api/personal-todos")
    suspend fun personalTodos(): TodosResponse

    @POST("api/personal-todos")
    suspend fun createPersonalTodo(@Body body: CreateTodoRequest): TodoResponse

    @PATCH("api/personal-todos/{id}")
    suspend fun updatePersonalTodo(
        @Path("id") id: Int,
        @Body body: UpdateTodoRequest,
    ): TodoResponse

    @DELETE("api/personal-todos/{id}")
    suspend fun deletePersonalTodo(@Path("id") id: Int): SuccessResponse

    @DELETE("api/personal-todos/clear-completed")
    suspend fun clearCompletedTodos(): SuccessResponse

    // ---- Approvals ----
    // Projects are read-only here: creating and configuring one stays on the web.

    @GET("api/approvals/counts")
    suspend fun approvalCounts(): ApprovalCountsDto

    @GET("api/my-approvals")
    suspend fun myApprovals(
        @Query("page") page: Int? = null,
        @Query("search") search: String? = null,
    ): MyApprovalsResponse

    @GET("api/my-approvals/trail")
    suspend fun approvalTrail(
        @Query("page") page: Int? = null,
        @Query("decision") decision: String? = null,
        @Query("project_id") projectId: Int? = null,
        @Query("search") search: String? = null,
    ): ApprovalTrailResponse

    @GET("api/my-requests")
    suspend fun myRequests(
        @Query("page") page: Int? = null,
        @Query("status") status: String? = null,
        @Query("approval_project_id") projectId: Int? = null,
        @Query("search") search: String? = null,
    ): MyRequestsResponse

    // Returns a bare Laravel paginator, not a wrapped envelope.
    @GET("api/approval-projects")
    suspend fun approvalProjects(
        @Query("page") page: Int? = null,
        @Query("search") search: String? = null,
        @Query("archived") archived: Boolean? = null,
    ): Paginated<ApprovalProjectDto>

    // Projects this person may raise a request against. A pure requestor (can_request
    // without approver access) is 403 on the list and show routes, so the New Request
    // flow starts here rather than there.
    @GET("api/approval-projects/available")
    suspend fun availableApprovalProjects(): AvailableApprovalProjectsResponse

    @GET("api/approval-projects/{projectId}")
    suspend fun approvalProject(@Path("projectId") projectId: Int): ApprovalProjectResponse

    /** Field definitions and sections the New Request screen renders itself from. */
    @GET("api/approval-projects/{projectId}/request-form")
    suspend fun approvalRequestForm(
        @Path("projectId") projectId: Int,
    ): ApprovalRequestFormResponse

    @GET("api/approval-projects/{projectId}/items")
    suspend fun approvalItems(
        @Path("projectId") projectId: Int,
        @Query("page") page: Int? = null,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        // Either a section id or the literal "none" for unsectioned requests.
        @Query("section_id") sectionId: String? = null,
        @Query("archived") archived: Boolean? = null,
    ): Paginated<ApprovalItemDto>

    @GET("api/approval-projects/{projectId}/items/{itemId}")
    suspend fun approvalItem(
        @Path("projectId") projectId: Int,
        @Path("itemId") itemId: Int,
    ): ApprovalItemDetailResponse

    // Multipart because a new request can carry up to 5 attachments. Custom field
    // values go as customFieldValues[<fieldId>] parts, which is what the server reads.
    @Multipart
    @POST("api/approval-projects/{projectId}/items")
    suspend fun createApprovalItem(
        @Path("projectId") projectId: Int,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody?,
        @Part("approval_section_id") sectionId: RequestBody?,
        @PartMap customFieldValues: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part attachments: List<MultipartBody.Part>,
    ): ApprovalItemResponse

    @PUT("api/approval-projects/{projectId}/items/{itemId}")
    suspend fun updateApprovalItem(
        @Path("projectId") projectId: Int,
        @Path("itemId") itemId: Int,
        @Body body: UpdateApprovalItemRequest,
    ): ApprovalItemResponse

    /** The decision itself: action is "approved" or "rejected". */
    @POST("api/approval-projects/{projectId}/items/{itemId}/advance")
    suspend fun advanceApprovalItem(
        @Path("projectId") projectId: Int,
        @Path("itemId") itemId: Int,
        @Body body: ApprovalAdvanceRequest,
    ): ApprovalAdvanceResponse

    @POST("api/approval-projects/{projectId}/items/{itemId}/resubmit")
    suspend fun resubmitApprovalItem(
        @Path("projectId") projectId: Int,
        @Path("itemId") itemId: Int,
    ): ApprovalItemResponse

    /** Cancels the request: a soft delete plus a workflow cancel, server-side. */
    @DELETE("api/approval-projects/{projectId}/items/{itemId}")
    suspend fun cancelApprovalItem(
        @Path("projectId") projectId: Int,
        @Path("itemId") itemId: Int,
    ): SuccessResponse

    // Oldest first, 30 to a page.
    @GET("api/approval-projects/{projectId}/items/{itemId}/comments")
    suspend fun approvalItemComments(
        @Path("projectId") projectId: Int,
        @Path("itemId") itemId: Int,
        @Query("page") page: Int? = null,
    ): Paginated<ApprovalCommentDto>

    @Multipart
    @POST("api/approval-projects/{projectId}/items/{itemId}/comments")
    suspend fun addApprovalItemComment(
        @Path("projectId") projectId: Int,
        @Path("itemId") itemId: Int,
        @Part("body") body: RequestBody,
        @Part attachments: List<MultipartBody.Part>,
    ): ApprovalCommentResponse

    // ---- Mirrors of web pages (WMT 54a6ae4) ----

    /** Approval requests other people shared with me; 15 a page. */
    @GET("api/shared-requests")
    suspend fun sharedRequests(
        @Query("page") page: Int = 1,
        @Query("search") search: String? = null,
    ): SharedRequestsResponse

    /** Cover for approvers who are away: mine, ones naming me, or all for manage-users. */
    @GET("api/approval-delegations")
    suspend fun approvalCover(): DelegationsResponse

    @POST("api/approval-delegations")
    suspend fun createApprovalCover(@Body body: CreateDelegationRequest): CreateDelegationResponse

    @DELETE("api/approval-delegations/{id}")
    suspend fun removeApprovalCover(@Path("id") id: Int): RemoveDelegationResponse

    /** Active people, for pickers. */
    @GET("api/people-options")
    suspend fun peopleOptions(): PeopleOptionsResponse

    /** The time corrections queue: "to_decide" or "mine", by status. */
    @GET("api/time-corrections")
    suspend fun timeCorrections(
        @Query("tab") tab: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
    ): TimeCorrectionsResponse

    /** A head's or leader's people, by the org chart. 403 for anybody heading nothing. */
    @GET("api/my-personnel")
    suspend fun myPersonnel(): MyPersonnelResponse

    @GET("api/my-personnel/overdue")
    suspend fun personnelOverdue(): PersonnelOverdueResponse

    // ---- Notes ----

    @GET("api/notes")
    suspend fun notes(
        @Query("scope") scope: String,
        @Query("q") query: String? = null,
        @Query("folder") folder: String? = null,
    ): NotesResponse

    @GET("api/notes/{id}")
    suspend fun note(@Path("id") id: Int): NoteResponse

    @POST("api/notes")
    suspend fun createNote(@Body body: NoteWriteRequest): NoteResponse

    @POST("api/notes/{id}/autosave")
    suspend fun autosaveNote(@Path("id") id: Int, @Body body: NoteWriteRequest): NoteSavedResponse

    @POST("api/notes/{id}/archive")
    suspend fun archiveNote(@Path("id") id: Int): MessageResponse

    @POST("api/notes/{id}/unarchive")
    suspend fun unarchiveNote(@Path("id") id: Int): MessageResponse

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") id: Int)
}
