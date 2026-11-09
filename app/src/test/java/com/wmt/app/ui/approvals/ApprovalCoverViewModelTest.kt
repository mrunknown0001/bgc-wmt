package com.wmt.app.ui.approvals

import com.wmt.app.domain.model.ApprovalCover
import com.wmt.app.domain.model.PersonOption
import com.wmt.app.domain.model.PersonnelOverdue
import com.wmt.app.domain.model.PersonnelUnit
import com.wmt.app.domain.model.User
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.PeopleRepository
import com.wmt.app.testing.MainDispatcherRule
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * A refusal to arrange cover must always arrive somewhere a person can read it.
 *
 * The form has fields for three keys, and errors on those are shown in place. Anything
 * else the server refuses on used to vanish: a validation error makes the field map
 * non-empty, which suppressed the snackbar, while no field on screen rendered the key it
 * came under. The button looked broken. Overlap is the case that matters, because the
 * server is free to report it however it likes.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApprovalCoverViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakePeople(var addResult: Resource<Int>) : PeopleRepository {
        override suspend fun peopleOptions(): Resource<List<PersonOption>> =
            Resource.Success(emptyList())

        override suspend fun approvalCover(): Resource<Pair<List<ApprovalCover>, Boolean>> =
            Resource.Success(emptyList<ApprovalCover>() to false)

        override suspend fun createApprovalCover(
            userId: Int,
            delegateId: Int,
            startsOn: String,
            endsOn: String?,
            reason: String?,
        ): Resource<Int> = addResult

        override suspend fun removeApprovalCover(id: Int): Resource<Int> = Resource.Success(0)

        override suspend fun myPersonnel(): Resource<List<PersonnelUnit>> = Resource.Success(emptyList())

        override suspend fun headsAnyone(): Boolean = false

        override suspend fun personnelOverdue(): Resource<PersonnelOverdue> =
            error("not used by the cover screen")
    }

    private class FakeAuth(private val user: User?) : AuthRepository {
        override val isLoggedIn: Flow<Boolean> = flowOf(user != null)
        override val currentUser: Flow<User?> = flowOf(user)
        override val tokenExpiresAt: Flow<Long?> = flowOf(null)
        override suspend fun login(email: String, password: String) = error("not used")
        override suspend fun logout() = error("not used")
        override suspend fun refreshProfile() = error("not used")
        override suspend fun changePassword(c: String, n: String, cf: String) = error("not used")
        override suspend fun logoutOtherDevices(password: String) = error("not used")
        override suspend fun refreshToken() = error("not used")
        override suspend fun registerDeviceToken(token: String) = error("not used")
    }

    private val me = User(
        id = 3,
        name = "Admin",
        email = "admin@wmt.com",
        position = null,
        department = null,
        team = null,
        roles = emptyList(),
    )

    private fun add(result: Resource<Int>): ApprovalCoverViewModel {
        val vm = ApprovalCoverViewModel(FakePeople(result), FakeAuth(me))
        vm.add(delegateId = 9, startsOn = "2026-12-01", endsOn = "2026-12-10", reason = null)
        return vm
    }

    @Test
    fun `an overlap reported under a key the form has no field for is still said`() = runTest(mainDispatcherRule.scheduler) {
        val vm = add(
            Resource.Error(
                AppError.Validation(
                    fieldErrors = mapOf("cover" to listOf("That overlaps cover you already have.")),
                    summary = "The given data was invalid.",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("That overlaps cover you already have.", vm.state.value.message)
    }

    @Test
    fun `an error on a field the form shows is left to that field`() = runTest(mainDispatcherRule.scheduler) {
        val vm = add(
            Resource.Error(
                AppError.Validation(
                    fieldErrors = mapOf("ends_on" to listOf("Must be after the start date.")),
                    summary = "The given data was invalid.",
                ),
            ),
        )
        advanceUntilIdle()

        assertNull("saying it twice is worse than once", vm.state.value.message)
        assertEquals(listOf("Must be after the start date."), vm.state.value.addErrors["ends_on"])
    }

    @Test
    fun `a refusal that is not a validation error is said as it stands`() = runTest(mainDispatcherRule.scheduler) {
        val vm = add(Resource.Error(AppError.Forbidden("You may not arrange cover.")))
        advanceUntilIdle()

        assertEquals("You may not arrange cover.", vm.state.value.message)
    }

    @Test
    fun `a validation error carrying no fields at all still says something`() = runTest(mainDispatcherRule.scheduler) {
        val vm = add(
            Resource.Error(
                AppError.Validation(fieldErrors = emptyMap(), summary = "That cover overlaps."),
            ),
        )
        advanceUntilIdle()

        assertNotNull("an empty field map must not swallow the refusal", vm.state.value.message)
    }

    @Test
    fun `cover that is arranged says how many approvals it reached`() = runTest(mainDispatcherRule.scheduler) {
        val vm = add(Resource.Success(1))
        advanceUntilIdle()

        val message = vm.state.value.message
        assertNotNull(message)
        // Singular, because one approval is one approval.
        assertEquals(true, message!!.contains("1 waiting approval is"))
    }
}
