package com.wmt.app.data.repository

import com.wmt.app.data.local.datastore.PreferencesManager
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val prefs: PreferencesManager,
) : SettingsRepository {

    override val serverUrl: Flow<String?> = prefs.serverUrl

    override val themeMode: Flow<String> = prefs.themeMode

    override suspend fun setThemeMode(mode: String) = prefs.saveThemeMode(mode)

    override suspend fun checkHealth(baseUrl: String): Resource<Unit> {
        val url = baseUrl.trim().trimEnd('/') + "/api/health"
        return try {
            val response = api.health(url)
            if (response.isSuccessful) Resource.Success(Unit)
            else Resource.Error(AppError.Unknown("Server responded with HTTP ${response.code()} for $url"))
        } catch (e: SocketTimeoutException) {
            Resource.Error(AppError.Timeout)
        } catch (e: Exception) {
            Resource.Error(AppError.Unknown("${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    override suspend fun saveServerUrl(baseUrl: String) = prefs.saveServerUrl(baseUrl)

    override suspend fun clearServerUrl() = prefs.clearServerUrl()

    override val maxUploadSizeMb: Flow<Int> = prefs.maxUploadSizeMb

    override suspend fun refreshAppSettings() {
        runCatching { api.appSettings() }
            .onSuccess { prefs.saveMaxUploadSizeMb(it.maxUploadSize) }
    }
}
