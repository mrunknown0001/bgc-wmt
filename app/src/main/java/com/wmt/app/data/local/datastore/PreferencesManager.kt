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
import com.wmt.app.data.remote.SessionManager
import com.wmt.app.data.remote.dto.UserDto
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.domain.model.User
import com.wmt.app.util.Constants
import com.wmt.app.util.DateUtils
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MAX_UPLOAD_MB = intPreferencesKey("max_upload_mb")
    }

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        Constants.ENCRYPTED_PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val userAdapter = moshi.adapter(UserDto::class.java)

    private val _token = MutableStateFlow(securePrefs.getString(TOKEN_KEY, null))
    private val _tokenExpiresAt = MutableStateFlow(
        securePrefs.getLong(TOKEN_EXPIRES_AT_KEY, 0L).takeIf { it > 0L },
    )
    val token: Flow<String?> = _token.asStateFlow()
    val isLoggedIn: Flow<Boolean> = _token.map { !it.isNullOrBlank() }

    /** Epoch millis at which the stored token lapses; null when the server did not say. */
    val tokenExpiresAt: Flow<Long?> = _tokenExpiresAt.asStateFlow()

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

    /** Server's general attachment cap in MB (default matches the backend seed). */
    val maxUploadSizeMb: Flow<Int> = prefs.map { it[Keys.MAX_UPLOAD_MB] ?: 10 }

    suspend fun saveMaxUploadSizeMb(mb: Int) {
        context.dataStore.edit { it[Keys.MAX_UPLOAD_MB] = mb.coerceAtLeast(1) }
    }

    val currentUser: Flow<User?> = prefs.map { p ->
        p[Keys.USER_JSON]?.let { runCatching { userAdapter.fromJson(it)?.toDomain() }.getOrNull() }
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

    /**
     * Stores a freshly issued token and the expiry that came with it. Written together
     * so a rotated token can never be paired with the previous token's deadline.
     */
    fun saveToken(token: String, expiresAtIso: String? = null) {
        val expiresAtMillis = DateUtils.parseInstant(expiresAtIso)?.toEpochMilli()
        val editor = securePrefs.edit().putString(TOKEN_KEY, token)
        if (expiresAtMillis != null) {
            editor.putLong(TOKEN_EXPIRES_AT_KEY, expiresAtMillis)
        } else {
            editor.remove(TOKEN_EXPIRES_AT_KEY)
        }
        editor.apply()
        _token.value = token
        _tokenExpiresAt.value = expiresAtMillis
        sessionManager.token = token
    }

    fun clearToken() {
        securePrefs.edit().remove(TOKEN_KEY).remove(TOKEN_EXPIRES_AT_KEY).apply()
        _token.value = null
        _tokenExpiresAt.value = null
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

    /** Wipes user-scoped state on logout (keeps the configured server URL). */
    suspend fun clearSession() {
        clearToken()
        context.dataStore.edit { p ->
            p.remove(Keys.USER_JSON)
            p.remove(Keys.UNREAD_COUNT)
            // Legacy key from the removed per-user notification preferences feature.
            p.remove(stringPreferencesKey("notif_prefs"))
        }
    }

    companion object {
        private const val TOKEN_KEY = "auth_token"
        private const val TOKEN_EXPIRES_AT_KEY = "auth_token_expires_at"
    }
}
