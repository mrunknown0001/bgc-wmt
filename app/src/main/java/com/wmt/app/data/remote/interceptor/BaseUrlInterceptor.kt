package com.wmt.app.data.remote.interceptor

import com.wmt.app.data.remote.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit is built with the placeholder base `http://localhost/`. Relative calls
 * therefore resolve against host `localhost`; this interceptor rewrites those to the
 * server URL the user configured at runtime. Absolute `@Url` requests (e.g. the health
 * probe, which targets a real host) are left untouched.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != PLACEHOLDER_HOST) {
            return chain.proceed(original)
        }
        val configured = sessionManager.serverUrl?.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }

    companion object {
        const val PLACEHOLDER_HOST = "localhost"
        const val PLACEHOLDER_BASE = "http://localhost/"
    }
}
