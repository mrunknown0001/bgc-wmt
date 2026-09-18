package com.wmt.app.data.remote.interceptor

import com.wmt.app.data.remote.SessionManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Where the bearer token is allowed to go on image and file loads.
 *
 * This interceptor exists because image URLs come from payloads rather than from the
 * app, so the usual "attach the token to everything" rule would hand this user's token
 * to whatever host a URL happened to name.
 */
class MediaAuthInterceptorTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var client: OkHttpClient
    private var lastResponseCode = 200

    @Before
    fun setUp() {
        sessionManager = SessionManager().apply {
            serverUrl = "https://wmt-dev.bfcgroup.ph"
            token = "29|abcdef"
        }
        val responder = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(lastResponseCode)
                .message("OK")
                .body("".toResponseBody("image/png".toMediaType()))
                .build()
        }
        client = OkHttpClient.Builder()
            .addInterceptor(MediaAuthInterceptor(sessionManager))
            .addInterceptor(responder)
            .build()
    }

    private fun authHeaderFor(url: String): String? =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            response.request.header("Authorization")
        }

    @Test
    fun `a file on the configured server gets the token`() {
        val header = authHeaderFor("https://wmt-dev.bfcgroup.ph/api/attachments/approval-item/3")

        assertEquals("Bearer 29|abcdef", header)
    }

    @Test
    fun `a host that is not the configured server gets nothing`() {
        // The case this interceptor exists for: an avatar or image URL pointing somewhere
        // else must not receive this user's token.
        assertNull(authHeaderFor("https://images.example.com/avatar.png"))
    }

    @Test
    fun `a lookalike hostname is not treated as the server`() {
        assertNull(authHeaderFor("https://wmt-dev.bfcgroup.ph.evil.example.com/avatar.png"))
    }

    @Test
    fun `host matching ignores case`() {
        assertNotNull(authHeaderFor("https://WMT-DEV.BFCGROUP.PH/api/attachments/task/1"))
    }

    @Test
    fun `no token means no header, and nothing breaks`() {
        sessionManager.token = null

        assertNull(authHeaderFor("https://wmt-dev.bfcgroup.ph/api/attachments/task/1"))
    }

    @Test
    fun `a blank token is treated as no token`() {
        sessionManager.token = "   "

        assertNull(authHeaderFor("https://wmt-dev.bfcgroup.ph/api/attachments/task/1"))
    }

    @Test
    fun `before a server is configured, nothing is authorised`() {
        sessionManager.serverUrl = null

        assertNull(authHeaderFor("https://wmt-dev.bfcgroup.ph/api/attachments/task/1"))
    }

    @Test
    fun `a 401 on a file does not end the session`() {
        // A missing or forbidden attachment should show as a broken image. Only the API
        // client decides a session is over, and it does that by clearing the token.
        lastResponseCode = 401

        authHeaderFor("https://wmt-dev.bfcgroup.ph/api/attachments/approval-item/3")

        assertEquals("29|abcdef", sessionManager.token)
    }
}
