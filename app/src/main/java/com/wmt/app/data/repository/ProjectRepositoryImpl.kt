package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.local.db.dao.ProjectDao
import com.wmt.app.data.local.db.toDomain
import com.wmt.app.data.local.db.toEntity
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.CreateProjectRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.Project
import com.wmt.app.domain.model.ProjectDetail
import com.wmt.app.domain.model.SearchResults
import com.wmt.app.domain.repository.ProjectRepository
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val dao: ProjectDao,
    private val moshi: Moshi,
) : ProjectRepository {

    override fun projects(): Flow<Resource<List<Project>>> = flow {
        val cached = dao.observeAll().first().map { it.toDomain() }
        emit(Resource.Loading(cached))

        when (val result = safeApiCall(moshi) { api.projects() }) {
            is Resource.Success -> {
                val projects = result.data.data.map { it.toDomain() }
                dao.replaceAll(projects.map { it.toEntity() })
                emit(Resource.Success(projects))
            }
            is Resource.Error -> emit(Resource.Error(result.error, cached))
            is Resource.Loading -> Unit
        }
    }

    override suspend fun searchProjects(query: String): Resource<List<Project>> =
        safeApiCall(moshi) { api.projects(query).data.map { it.toDomain() } }

    override suspend fun globalSearch(query: String): Resource<SearchResults> =
        safeApiCall(moshi) {
            val response = api.search(query)
            SearchResults(
                projects = response.projects.map { it.toDomain() },
                tasks = response.tasks.map { it.toDomain() },
            )
        }

    override suspend fun projectDetail(projectId: Int): Resource<ProjectDetail> =
        safeApiCall(moshi) { api.projectDetail(projectId).toDomain() }

    override suspend fun createProject(
        name: String,
        description: String?,
        status: String,
        dueDate: String?,
    ): Resource<Project> = safeApiCall(moshi) {
        api.createProject(
            CreateProjectRequest(
                name = name,
                description = description,
                status = status,
                dueDate = dueDate,
            ),
        ).project.toDomain()
    }

    override suspend fun updateProject(
        projectId: Int,
        name: String,
        description: String?,
        status: String,
        dueDate: String?,
    ): Resource<Project> = safeApiCall(moshi) {
        api.updateProject(
            projectId,
            CreateProjectRequest(
                name = name,
                description = description,
                status = status,
                dueDate = dueDate,
            ),
        ).project.toDomain()
    }

    override suspend fun deleteProject(projectId: Int): Resource<Unit> =
        safeApiCall(moshi) { api.deleteProject(projectId) }
}
