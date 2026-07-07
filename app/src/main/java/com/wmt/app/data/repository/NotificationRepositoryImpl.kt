package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.local.datastore.PreferencesManager
import com.wmt.app.data.local.db.dao.NotificationDao
import com.wmt.app.data.local.db.toDomain
import com.wmt.app.data.local.db.toEntity
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.NotificationPreferenceRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.di.ApplicationScope
import com.wmt.app.domain.model.Notification
import com.wmt.app.domain.repository.NotificationRepository
import com.wmt.app.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val dao: NotificationDao,
    private val prefs: PreferencesManager,
    private val moshi: Moshi,
    @ApplicationScope private val appScope: CoroutineScope,
) : NotificationRepository {

    override val unreadCount: Flow<Int> = prefs.unreadCount
    override val preferences: Flow<Map<String, Boolean>> = prefs.notificationPreferences

    override fun notifications(filter: String?): Flow<Resource<List<Notification>>> = flow {
        // Only the default inbox list is cached offline; filtered views go straight to the API.
        val useCache = filter == null || filter == "inbox"
        val cached = if (useCache) dao.observeAll().first().map { it.toDomain() } else emptyList()
        emit(Resource.Loading(if (useCache) cached else null))

        when (val result = safeApiCall(moshi) { api.notifications(filter) }) {
            is Resource.Success -> {
                val items = result.data.data.map { it.toDomain() }
                if (useCache) dao.replaceAll(items.map { it.toEntity() })
                emit(Resource.Success(items))
            }
            is Resource.Error -> emit(Resource.Error(result.error, if (useCache) cached else null))
            is Resource.Loading -> Unit
        }
    }

    override suspend fun toggleBookmark(id: String): Resource<Unit> =
        safeApiCall(moshi) {
            api.toggleNotificationBookmark(id)
            Unit
        }

    override suspend fun archive(id: String): Resource<Unit> =
        safeApiCall(moshi) {
            api.archiveNotification(id)
            Unit
        }

    override suspend fun unarchive(id: String): Resource<Unit> =
        safeApiCall(moshi) {
            api.unarchiveNotification(id)
            Unit
        }

    override suspend fun refreshUnreadCount(): Resource<Int> {
        val result = safeApiCall(moshi) { api.unreadCount().count }
        if (result is Resource.Success) prefs.saveUnreadCount(result.data)
        return result
    }

    override fun incrementUnreadLocally() {
        appScope.launch { prefs.incrementUnreadCount() }
    }

    override suspend fun markRead(id: String): Resource<Unit> {
        val result = safeApiCall(moshi) {
            api.markNotificationRead(id)
            Unit
        }
        if (result is Resource.Success) {
            dao.markRead(id, Instant.now().toString())
            val current = prefs.unreadCount.first()
            prefs.saveUnreadCount((current - 1).coerceAtLeast(0))
        }
        return result
    }

    override suspend fun markAllRead(): Resource<Unit> {
        val result = safeApiCall(moshi) {
            api.markAllNotificationsRead()
            Unit
        }
        if (result is Resource.Success) {
            dao.markAllRead(Instant.now().toString())
            prefs.saveUnreadCount(0)
        }
        return result
    }

    override suspend fun refreshPreferences(): Resource<Unit> {
        val result = safeApiCall(moshi) { api.notificationPreferences() }
        if (result is Resource.Success) prefs.saveNotificationPreferences(result.data)
        return when (result) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> Resource.Error(result.error)
            is Resource.Loading -> Resource.Loading()
        }
    }

    override suspend fun setPreference(type: String, enabled: Boolean): Resource<Unit> {
        // Optimistically update the local cache, then persist to the server.
        val updated = prefs.notificationPreferences.first().toMutableMap().apply { this[type] = enabled }
        prefs.saveNotificationPreferences(updated)
        return safeApiCall(moshi) {
            api.updateNotificationPreference(NotificationPreferenceRequest(type, enabled))
            Unit
        }
    }
}
