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

private fun HttpException.toAppError(moshi: Moshi): AppError = when (code()) {
    401 -> AppError.Unauthorized
    422 -> parseValidation(moshi) ?: AppError.Unknown("Validation failed")
    in 500..599 -> AppError.Server
    else -> AppError.Unknown("Request failed (${code()})")
}

private fun HttpException.parseValidation(moshi: Moshi): AppError.Validation? = runCatching {
    val raw = response()?.errorBody()?.string().orEmpty()
    val dto = moshi.adapter(ValidationErrorDto::class.java).fromJson(raw)
    val errors = dto?.errors.orEmpty()
    AppError.Validation(
        fieldErrors = errors,
        summary = dto?.message
            ?: errors.values.firstOrNull()?.firstOrNull()
            ?: "Validation failed",
    )
}.getOrNull()
