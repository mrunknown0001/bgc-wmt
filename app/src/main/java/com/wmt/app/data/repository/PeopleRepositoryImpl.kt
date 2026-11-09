package com.wmt.app.data.repository

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.api.WmtApi
import com.wmt.app.data.remote.dto.CreateDelegationRequest
import com.wmt.app.data.remote.dto.toDomain
import com.wmt.app.data.remote.safeApiCall
import com.wmt.app.domain.model.ApprovalCover
import com.wmt.app.domain.model.PersonOption
import com.wmt.app.domain.model.PersonnelOverdue
import com.wmt.app.domain.model.PersonnelUnit
import com.wmt.app.util.AppError
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

    /** Null until asked. A head stays a head for a session; a reorganisation can wait for the next. */
    @Volatile private var heads: Boolean? = null

    override suspend fun myPersonnel(): Resource<List<PersonnelUnit>> {
        val result = safeApiCall(moshi) { api.myPersonnel().toDomain() }
        when {
            result is Resource.Success -> heads = true
            result is Resource.Error && result.error is AppError.Forbidden -> heads = false
        }
        return result
    }

    override suspend fun personnelOverdue(): Resource<PersonnelOverdue> =
        safeApiCall(moshi) { api.personnelOverdue().toDomain() }

    override suspend fun personOverview(userId: Int): Resource<com.wmt.app.domain.model.PersonOverview> =
        safeApiCall(moshi) { api.personOverview(userId).toDomain() }

    override suspend fun headsAnyone(): Boolean {
        heads?.let { return it }
        // Only a definite answer is remembered: a network failure asks again next time.
        myPersonnel()
        return heads ?: false
    }
}
