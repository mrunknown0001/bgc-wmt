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
