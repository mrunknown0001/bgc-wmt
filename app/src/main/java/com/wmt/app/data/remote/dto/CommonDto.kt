package com.wmt.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Laravel API-resource collection envelope: { "data": [ ... ] }. */
@JsonClass(generateAdapter = true)
data class ApiList<T>(
    @Json(name = "data") val data: List<T> = emptyList(),
)

/** Laravel API-resource single envelope: { "data": { ... } }. */
@JsonClass(generateAdapter = true)
data class ApiObject<T>(
    @Json(name = "data") val data: T,
)

@JsonClass(generateAdapter = true)
data class MessageResponse(
    @Json(name = "message") val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class UnreadCountResponse(
    @Json(name = "count") val count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class UserSummaryDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProjectSummaryDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
)

/** Shape of a Laravel 422 response body. */
@JsonClass(generateAdapter = true)
data class ValidationErrorDto(
    @Json(name = "message") val message: String? = null,
    @Json(name = "errors") val errors: Map<String, List<String>>? = null,
)
