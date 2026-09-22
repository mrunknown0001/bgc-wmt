package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.local.datastore.PreferencesManager
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.ChangePasswordRequest
import com.wmt.app.data.remote.dto.DeviceTokenRequest
import com.wmt.app.data.remote.dto.LoginRequest
import com.wmt.app.data.remote.dto.LogoutOtherDevicesRequest
import com.wmt.app.data.remote.dto.UserDto
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.User
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.util.AppError
import com.wmt.app.util.Constants
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val prefs: PreferencesManager,
    private val moshi: Moshi,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = prefs.isLoggedIn
    override val currentUser: Flow<User?> = prefs.currentUser
    override val tokenExpiresAt: Flow<Long?> = prefs.tokenExpiresAt

    override suspend fun login(email: String, password: String): Resource<User> {
        val result = safeApiCall(moshi) {
            api.login(LoginRequest(email, password, Constants.DEVICE_NAME))
        }
        return when (result) {
            is Resource.Success -> {
                prefs.saveToken(result.data.token, result.data.expiresAt)
                prefs.saveUser(result.data.user)
                seedNotificationPreferences(result.data.user)
                Resource.Success(result.data.user.toDomain())
            }
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    /**
     * The user payload carries the notification switches, so Profile has them without a
     * second round trip. Only a non-empty block is written: a server or cached payload
     * that omits it must not wipe switches already stored.
     */
    private suspend fun seedNotificationPreferences(user: UserDto) {
        if (user.notificationPreferences.isNotEmpty()) {
            prefs.saveNotificationPreferences(user.notificationPreferences)
        }
    }

    override suspend fun logout(): Resource<Unit> {
        // Best-effort server logout; always clear local state afterwards.
        runCatching { api.logout() }
        prefs.clearSession()
        return Resource.Success(Unit)
    }

    override suspend fun refreshProfile(): Resource<User> {
        val result = safeApiCall(moshi) { api.currentUser() }
        return when (result) {
            is Resource.Success -> {
                prefs.saveUser(result.data)
                seedNotificationPreferences(result.data)
                Resource.Success(result.data.toDomain())
            }
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
    ): Resource<String> {
        val result = safeApiCall(moshi) {
            api.changePassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    password = newPassword,
                    passwordConfirmation = confirmPassword,
                ),
            )
        }
        return when (result) {
            is Resource.Success -> Resource.Success(
                result.data.message ?: "Password changed successfully.",
            )
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    override suspend fun logoutOtherDevices(password: String): Resource<String> {
        val result = safeApiCall(moshi) { api.logoutOtherDevices(LogoutOtherDevicesRequest(password)) }
        return when (result) {
            is Resource.Success -> Resource.Success(
                result.data.message ?: "Signed out of other devices.",
            )
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    override suspend fun refreshToken(): Resource<Unit> {
        val result = safeApiCall(moshi) { api.refreshToken() }
        return when (result) {
            is Resource.Success -> {
                val fresh = result.data.token
                if (fresh.isBlank()) {
                    // Nothing usable came back. The old token is already gone server-side,
                    // but reporting the failure beats storing a blank and forcing a logout
                    // we cannot explain.
                    Resource.Error(AppError.Unknown("Token refresh returned no token."))
                } else {
                    prefs.saveToken(fresh, result.data.expiresAt)
                    Resource.Success(Unit)
                }
            }
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    override suspend fun registerDeviceToken(token: String): Resource<Unit> =
        safeApiCall(moshi) {
            api.registerDeviceToken(DeviceTokenRequest(token, Constants.PLATFORM))
            Unit
        }
}
