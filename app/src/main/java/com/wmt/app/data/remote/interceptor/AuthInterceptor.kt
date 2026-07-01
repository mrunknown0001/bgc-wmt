package com.wmt.app.data.remote.interceptor

import com.wmt.app.data.remote.SessionManager
import okhttp3.Interceptor
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
        val builder = original.newBuilder()
            .header("Accept", "application/json")

        sessionManager.token?.takeIf { it.isNotBlank() }?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(builder.build())
        if (response.code == 401) {
            sessionManager.notifyUnauthorized()
        }
        return response
    }
}
