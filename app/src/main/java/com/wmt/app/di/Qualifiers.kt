package com.wmt.app.di

import javax.inject.Qualifier

/** Application-lifetime [kotlinx.coroutines.CoroutineScope] for fire-and-forget work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
