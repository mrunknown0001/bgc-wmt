package com.wmt.app.di

import javax.inject.Qualifier

/** Application-lifetime [kotlinx.coroutines.CoroutineScope] for fire-and-forget work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The [okhttp3.OkHttpClient] used for images and file downloads, as opposed to the API
 * client. Same base-URL rewriting and host-scoped auth, without body logging.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaClient
