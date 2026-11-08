package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.CreateDelegationRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.ApprovalCover
import com.wmt.app.domain.model.PersonOption
import com.wmt.app.domain.repository.PeopleRepository
import com.wmt.app.util.Resource
import javax.inject.Inject
import javax.inject.Singleton

/** Network-only, like approvals: cover and the people behind it change under you. */
@Singleton
class PeopleRepositoryImpl @Inject constructor(
    private val api: WmtApi,
    private val moshi: Moshi,
) : PeopleRepository {

    override suspend fun peopleOptions(): Resource<List<PersonOption>> =
        safeApiCall(moshi) { api.peopleOptions().users.map { it.toDomain() } }

    override suspend fun approvalCover(): Resource<Pair<List<ApprovalCover>, Boolean>> =
        safeApiCall(moshi) {
            val response = api.approvalCover()
            response.delegations.map { it.toDomain() } to response.canManageOthers
        }

    override suspend fun createApprovalCover(
        userId: Int,
        delegateId: Int,
        startsOn: String,
        endsOn: String?,
        reason: String?,
    ): Resource<Int> = safeApiCall(moshi) {
        api.createApprovalCover(
            CreateDelegationRequest(
                userId = userId,
                delegateId = delegateId,
                startsOn = startsOn,
                endsOn = endsOn?.ifBlank { null },
                reason = reason?.trim()?.ifBlank { null },
            ),
        ).backfilled
    }

    override suspend fun removeApprovalCover(id: Int): Resource<Int> =
        safeApiCall(moshi) { api.removeApprovalCover(id).removed }
}
