package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "device_name") val deviceName: String,
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "token") val token: String,
    /** ISO-8601; null only if the server has no token expiry configured. */
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "user") val user: UserDto,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "email") val email: String = "",
    @Json(name = "position") val position: String? = null,
    @Json(name = "department") val department: NamedRefDto? = null,
    @Json(name = "team") val team: NamedRefDto? = null,
    @Json(name = "roles") val roles: List<String> = emptyList(),
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "capabilities") val capabilities: UserCapabilitiesDto? = null,
    /**
     * Email notification switches, keyed `email_task_assigned` and the like. The server
     * ships this block inside the login payload, so Profile can render the switches from
     * the cached session instead of waiting on `/api/notification-preferences`. Empty on
     * a payload cached before this field existed — hence a seed, not a source of truth:
     * an empty map never overwrites what is already stored.
     */
    @Json(name = "notification_preferences") val notificationPreferences: Map<String, Boolean> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class NamedRefDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
)

@JsonClass(generateAdapter = true)
data class DeviceTokenRequest(
    @Json(name = "token") val token: String,
    @Json(name = "platform") val platform: String,
)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    @Json(name = "current_password") val currentPassword: String,
    @Json(name = "password") val password: String,
    @Json(name = "password_confirmation") val passwordConfirmation: String,
)

@JsonClass(generateAdapter = true)
data class LogoutOtherDevicesRequest(
    @Json(name = "password") val password: String,
)

/** `POST /api/token/refresh` — a replacement token and its new expiry, no user payload. */
@JsonClass(generateAdapter = true)
data class TokenRefreshResponse(
    @Json(name = "token") val token: String = "",
    @Json(name = "expires_at") val expiresAt: String? = null,
)

/**
 * What the signed-in person is allowed to do, decided by the server so the rule is not
 * re-derived here. Gates the Approvals tab, its To Approve / My Requests split and the
 * New Request button, among others.
 *
 * Absent from an older cached user payload, hence the defaults: everything off, which
 * hides a feature rather than showing one that would then be refused.
 */
@JsonClass(generateAdapter = true)
data class UserCapabilitiesDto(
    @Json(name = "can_approve") val canApprove: Boolean = false,
    @Json(name = "can_request") val canRequest: Boolean = false,
    @Json(name = "can_expand_uploads") val canExpandUploads: Boolean = false,
    @Json(name = "can_create_project") val canCreateProject: Boolean = false,
    @Json(name = "can_manage_tasks") val canManageTasks: Boolean = false,
    @Json(name = "can_manage_projects") val canManageProjects: Boolean = false,
    /**
     * Broader than [canApprove] on purpose: admins and executives reach approvals
     * without it.
     */
    @Json(name = "can_access_approvals") val canAccessApprovals: Boolean = false,
)
