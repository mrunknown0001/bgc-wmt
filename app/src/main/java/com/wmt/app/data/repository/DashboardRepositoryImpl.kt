package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.local.db.dao.DashboardDao
import com.wmt.app.data.local.db.entity.DashboardEntity
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.DashboardDto
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.DashboardData
import com.wmt.app.domain.repository.DashboardRepository
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val dao: DashboardDao,
    private val moshi: Moshi,
) : DashboardRepository {

    private val adapter = moshi.adapter(DashboardDto::class.java)

    override fun dashboard(): Flow<Resource<DashboardData>> = flow {
        val cached = dao.observe().first()?.json
            ?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
            ?.toDomain()
        emit(Resource.Loading(cached))

        when (val result = safeApiCall(moshi) { api.dashboard() }) {
            is Resource.Success -> {
                dao.upsert(DashboardEntity(0, adapter.toJson(result.data)))
                emit(Resource.Success(result.data.toDomain()))
            }
            is Resource.Error -> emit(Resource.Error(result.error, cached))
            is Resource.Loading -> Unit
        }
    }
}
