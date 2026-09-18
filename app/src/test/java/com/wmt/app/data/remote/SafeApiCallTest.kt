package com.wmt.app.data.remote

import com.squareup.moshi.Moshi
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Covers the HTTP-to-[AppError] mapping. The response bodies here are the real ones the
 * staging API returns, so a server-side change of shape shows up as a failure here rather
 * than as a generic "something went wrong" on a phone.
 */
class SafeApiCallTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `429 carries the server message and the Retry-After delta`() = runTest {
        // Verbatim from the per-email+IP login lock on staging.
        val result = mapError(
            code = 429,
            body = """{"message":"Too many login attempts. Please try again in 49 seconds."}""",
            headers = Headers.headersOf("Retry-After", "58"),
        )

        assertTrue("expected RateLimited, got $result", result is AppError.RateLimited)
        result as AppError.RateLimited
        assertEquals(58, result.retryAfterSeconds)
        assertEquals("Too many login attempts. Please try again in 49 seconds.", result.message)
    }

    @Test
    fun `429 without a Retry-After header still surfaces the message`() = runTest {
        val result = mapError(429, """{"message":"Too Many Attempts."}""")

        assertTrue(result is AppError.RateLimited)
        assertEquals(null, (result as AppError.RateLimited).retryAfterSeconds)
        assertEquals("Too Many Attempts.", result.message)
    }

    @Test
    fun `403 reports the server's reason rather than a code`() = runTest {
        val result = mapError(403, """{"message":"This action is unauthorized."}""")

        assertTrue(result is AppError.Forbidden)
        assertEquals("This action is unauthorized.", result.message)
    }

    @Test
    fun `422 keeps field errors addressable and summarises with the server message`() = runTest {
        // The dependency-blocked status change arrives under errors.status.
        val result = mapError(
            code = 422,
            body = """{"message":"The status field is invalid.","errors":{"status":["Finish the blocking task first."]}}""",
        )

        assertTrue(result is AppError.Validation)
        result as AppError.Validation
        assertEquals("Finish the blocking task first.", result.fieldErrors["status"]?.first())
        assertEquals("The status field is invalid.", result.message)
    }

    @Test
    fun `422 with no message falls back to the first field error`() = runTest {
        val result = mapError(422, """{"errors":{"email":["These credentials are wrong."]}}""")

        assertEquals("These credentials are wrong.", result.message)
    }

    @Test
    fun `401 stays a plain Unauthorized so the session-clearing path is unambiguous`() = runTest {
        assertEquals(AppError.Unauthorized, mapError(401, """{"message":"Unauthenticated."}"""))
    }

    @Test
    fun `5xx is generic — a stack trace is not something to show a person`() = runTest {
        assertEquals(AppError.Server, mapError(500, """{"message":"Server Error"}"""))
    }

    @Test
    fun `an unmapped code still prefers the server message over the code`() = runTest {
        val result = mapError(418, """{"message":"I refuse to brew coffee."}""")

        assertEquals("I refuse to brew coffee.", result.message)
    }

    @Test
    fun `an empty error body degrades to the code rather than throwing`() = runTest {
        assertEquals("Request failed (418)", mapError(418, "").message)
    }

    /** Runs [safeApiCall] over a thrown [HttpException] and returns the mapped error. */
    private suspend fun mapError(
        code: Int,
        body: String,
        headers: Headers = Headers.headersOf(),
    ): AppError {
        val result = safeApiCall<Unit>(moshi) { throw httpException(code, body, headers) }
        assertTrue("expected an error for HTTP $code", result is Resource.Error)
        return (result as Resource.Error).error
    }

    private fun httpException(code: Int, body: String, headers: Headers): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .headers(headers)
            .request(Request.Builder().url("https://wmt-dev.bfcgroup.ph/api/probe").build())
            .build()
        return HttpException(
            Response.error<Unit>(body.toResponseBody("application/json".toMediaType()), raw),
        )
    }
}
