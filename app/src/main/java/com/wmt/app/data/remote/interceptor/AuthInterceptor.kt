package com.wmt.app.data.remote.interceptor

import com.wmt.app.data.remote.SessionManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Attaches the Sanctum bearer token and reports 401s for forced logout. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val sentToken = sessionManager.token?.takeIf { it.isNotBlank() }
        val response = chain.proceed(original.withAuth(sentToken))

        if (response.code != 401) return response

        // /api/token/refresh drops the old token the instant it answers, so a request that
        // set off with the previous one comes back 401 through no fault of the session.
        // A token that changed underneath this call means rotation, not a dead session.
        val currentToken = sessionManager.token?.takeIf { it.isNotBlank() }
        if (currentToken != null && currentToken != sentToken) {
            // A one-shot body (attachments stream straight from the ContentResolver and
            // are never buffered) cannot be written twice, so report the failure and let
            // the person retry the upload — either way, this is not a logout.
            if (original.body?.isOneShot() == true) return response

            response.close()
            return chain.proceed(original.withAuth(currentToken))
        }

        sessionManager.notifyUnauthorized()
        return response
    }

    private fun Request.withAuth(token: String?): Request {
        val builder = newBuilder().header("Accept", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }
}
