package com.wmt.app.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.wmt.app.data.remote.SessionManager
import com.wmt.app.data.remote.dto.UserDto
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.domain.model.User
import com.wmt.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = Constants.DATASTORE_PREFS)

/**
 * Persists server URL, notification preferences, cached user and unread count in
 * DataStore, and the auth token in [EncryptedSharedPreferences]. Mirrors the values
 * the OkHttp interceptors need into [SessionManager].
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val sessionManager: SessionManager,
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val UNREAD_COUNT = intPreferencesKey("unread_count")
        val USER_JSON = stringPreferencesKey("user_json")
        val NOTIF_PREFS = stringPreferencesKey("notif_prefs")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        Constants.ENCRYPTED_PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val mapAdapter =
        moshi.adapter<Map<String, Boolean>>(
            Types.newParameterizedType(Map::class.java, String::class.java, java.lang.Boolean::class.java),
        )
    private val userAdapter = moshi.adapter(UserDto::class.java)

    private val _token = MutableStateFlow(securePrefs.getString(TOKEN_KEY, null))
    val token: Flow<String?> = _token.asStateFlow()
    val isLoggedIn: Flow<Boolean> = _token.map { !it.isNullOrBlank() }

    private val prefs: Flow<androidx.datastore.preferences.core.Preferences> =
        context.dataStore.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    val serverUrl: Flow<String?> = prefs.map { it[Keys.SERVER_URL] }

    /** "system" | "light" | "dark". */
    val themeMode: Flow<String> = prefs.map { it[Keys.THEME_MODE] ?: "system" }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    init {
        // Seed the interceptor caches synchronously so the very first request is authed.
        // NB: must run after `prefs`/`serverUrl` are initialised, since it reads them.
        sessionManager.token = _token.value
        sessionManager.serverUrl = runBlocking { readServerUrl() }
    }

    val unreadCount: Flow<Int> = prefs.map { it[Keys.UNREAD_COUNT] ?: 0 }

    val currentUser: Flow<User?> = prefs.map { p ->
        p[Keys.USER_JSON]?.let { runCatching { userAdapter.fromJson(it)?.toDomain() }.getOrNull() }
    }

    val notificationPreferences: Flow<Map<String, Boolean>> = prefs.map { p ->
        p[Keys.NOTIF_PREFS]?.let { runCatching { mapAdapter.fromJson(it) }.getOrNull() } ?: emptyMap()
    }

    private suspend fun readServerUrl(): String? = serverUrl.first()

    suspend fun saveServerUrl(url: String) {
        val normalised = url.trim().trimEnd('/')
        context.dataStore.edit { it[Keys.SERVER_URL] = normalised }
        sessionManager.serverUrl = normalised
    }

    suspend fun clearServerUrl() {
        context.dataStore.edit { it.remove(Keys.SERVER_URL) }
        sessionManager.serverUrl = null
    }

    fun saveToken(token: String) {
        securePrefs.edit().putString(TOKEN_KEY, token).apply()
        _token.value = token
        sessionManager.token = token
    }

    fun clearToken() {
        securePrefs.edit().remove(TOKEN_KEY).apply()
        _token.value = null
        sessionManager.token = null
    }

    suspend fun saveUser(user: UserDto) {
        context.dataStore.edit { it[Keys.USER_JSON] = userAdapter.toJson(user) }
    }

    suspend fun saveUnreadCount(count: Int) {
        context.dataStore.edit { it[Keys.UNREAD_COUNT] = count.coerceAtLeast(0) }
    }

    suspend fun incrementUnreadCount() {
        context.dataStore.edit { p ->
            p[Keys.UNREAD_COUNT] = (p[Keys.UNREAD_COUNT] ?: 0) + 1
        }
    }

    suspend fun saveNotificationPreferences(prefsMap: Map<String, Boolean>) {
        context.dataStore.edit { it[Keys.NOTIF_PREFS] = mapAdapter.toJson(prefsMap) }
    }

    /** Wipes user-scoped state on logout (keeps the configured server URL). */
    suspend fun clearSession() {
        clearToken()
        context.dataStore.edit { p ->
            p.remove(Keys.USER_JSON)
            p.remove(Keys.UNREAD_COUNT)
            p.remove(Keys.NOTIF_PREFS)
        }
    }

    companion object {
        private const val TOKEN_KEY = "auth_token"
    }
}
