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

    override val startupThemeMode: String get() = prefs.startupThemeMode

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

    override val videoMaxUploadSizeMb: Flow<Int> = prefs.videoMaxUploadSizeMb

    override val primaryColor: Flow<String?> = prefs.primaryColor

    override val startupPrimaryColor: String? get() = prefs.startupPrimaryColor

    override suspend fun refreshAppSettings() {
        runCatching { api.appSettings() }
            .onSuccess {
                prefs.saveMaxUploadSizeMb(it.maxUploadSize)
                prefs.saveVideoMaxUploadSizeMb(it.videoMaxUploadSize)
                // Only overwrite the cached accent when the server actually
                // named one; a null here means "not configured", which should
                // leave the last known colour alone rather than reset it.
                it.primaryColor?.takeIf { value -> value.isNotBlank() }
                    ?.let { value -> prefs.savePrimaryColor(value) }
            }
    }
}
