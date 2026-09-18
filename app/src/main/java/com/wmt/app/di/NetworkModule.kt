package com.wmt.app.di

import com.squareup.moshi.Moshi
import com.wmt.app.BuildConfig
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.interceptor.AuthInterceptor
import com.wmt.app.data.remote.interceptor.BaseUrlInterceptor
import com.wmt.app.data.remote.interceptor.MediaAuthInterceptor
import com.wmt.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrlInterceptor: BaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(Constants.DEFAULT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(Constants.DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS)
            // Generous write window so large comment attachments (videos up to 50MB) survive slow Wi-Fi.
            .writeTimeout(Constants.DEFAULT_WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Client for images and attachment downloads. Shares the base-URL rewriting and the
     * token, but not the body logging: printing an image response buffers the whole file
     * to log bytes nobody reads. Its auth is host-scoped, so a URL pointing anywhere
     * other than the configured server is fetched without the token.
     */
    @Provides
    @Singleton
    @MediaClient
    fun provideMediaOkHttpClient(
        baseUrlInterceptor: BaseUrlInterceptor,
        mediaAuthInterceptor: MediaAuthInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(mediaAuthInterceptor)
        .connectTimeout(Constants.DEFAULT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Constants.DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BaseUrlInterceptor.PLACEHOLDER_BASE)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideWmtApi(retrofit: Retrofit): WmtApi = retrofit.create(WmtApi::class.java)
}
