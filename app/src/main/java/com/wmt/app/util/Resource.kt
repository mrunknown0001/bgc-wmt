package com.wmt.app.util

/**
 * A generic wrapper describing the outcome of a data operation that may surface
 * cached data alongside a loading or error state.
 */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>

    data class Loading<T>(val data: T? = null) : Resource<T>

    data class Error<T>(
        val error: AppError,
        val data: T? = null,
    ) : Resource<T>
}

/** Domain-level classification of failures, mapped from HTTP/IO errors. */
sealed class AppError(val message: String) {
    data object Unauthorized : AppError("Your session has expired. Please log in again.")
    data class Validation(val fieldErrors: Map<String, List<String>>, val summary: String) :
        AppError(summary)
    data object Server : AppError("Something went wrong. Please try again.")
    data object NoConnection : AppError("No connection.")
    data object Timeout : AppError("Server not responding. Check your connection.")
    data class Unknown(val detail: String) : AppError(detail)
}
