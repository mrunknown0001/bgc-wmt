package com.wmt.app.data.remote

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.dto.ValidationErrorDto
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Runs [block] and normalises any failure into a [Resource.Error] carrying a
 * domain [AppError]. Used by the repository implementations.
 */
suspend fun <T> safeApiCall(
    moshi: Moshi,
    block: suspend () -> T,
): Resource<T> = try {
    Resource.Success(block())
} catch (e: HttpException) {
    Resource.Error(e.toAppError(moshi))
} catch (e: SocketTimeoutException) {
    Resource.Error(AppError.Timeout)
} catch (e: IOException) {
    Resource.Error(AppError.NoConnection)
} catch (e: Exception) {
    Resource.Error(AppError.Unknown(e.message ?: "Unexpected error"))
}

private fun HttpException.toAppError(moshi: Moshi): AppError {
    // The error body is a one-shot stream, so decode it before branching on the code.
    val parsed = parseErrorBody(moshi)
    return when (code()) {
        401 -> AppError.Unauthorized
        // A genuine "you may not do that" — never retried, and the server says why.
        403 -> AppError.Forbidden(parsed?.message ?: "You don't have permission to do that.")
        422 -> AppError.Validation(
            fieldErrors = parsed?.errors.orEmpty(),
            summary = parsed?.message
                ?: parsed?.errors?.values?.firstOrNull()?.firstOrNull()
                ?: "Validation failed",
        )
        429 -> AppError.RateLimited(
            detail = parsed?.message ?: "Too many attempts. Please try again shortly.",
            retryAfterSeconds = retryAfterSeconds(),
        )
        in 500..599 -> AppError.Server
        else -> AppError.Unknown(parsed?.message ?: "Request failed (${code()})")
    }
}

/** Decodes both Laravel error shapes: `{message, errors}` (422) and plain `{message}`. */
private fun HttpException.parseErrorBody(moshi: Moshi): ValidationErrorDto? {
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
    if (raw.isBlank()) return null
    return runCatching { moshi.adapter(ValidationErrorDto::class.java).fromJson(raw) }.getOrNull()
}

/**
 * Both throttles the API applies answer with `Retry-After` in delta-seconds — the
 * route's per-IP limiter and the per-email+IP login lock. Callers wait this out
 * rather than hammering.
 */
private fun HttpException.retryAfterSeconds(): Int? =
    response()?.headers()?.get("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
