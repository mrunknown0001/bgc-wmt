package com.wmt.app.data.remote.interceptor

import com.wmt.app.data.remote.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the bearer token to image and file loads, but only for the configured server.
 *
 * Attachments live behind the API guard, so fetching one needs the token. Unlike the API
 * client, though, this one is handed whatever URL a payload happens to carry -- an avatar
 * on someone else's CDN, say -- and a third-party host must never receive this user's
 * token, so the header goes on only when the request is addressed to our own server.
 *
 * It also stays quiet about 401s. A missing or forbidden file should show a broken image,
 * not end the session; the API client is what decides a session is over.
 */
@Singleton
class MediaAuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val serverHost = sessionManager.serverUrl?.toHttpUrlOrNull()?.host
        val token = sessionManager.token?.takeIf { it.isNotBlank() }

        val isOwnServer = serverHost != null && request.url.host.equals(serverHost, ignoreCase = true)
        if (token == null || !isOwnServer) {
            return chain.proceed(request)
        }

        return chain.proceed(
            request.newBuilder()
                .header("Accept", "*/*")
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}
