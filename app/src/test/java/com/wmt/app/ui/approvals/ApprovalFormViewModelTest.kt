package com.wmt.app.ui.approvals

import androidx.lifecycle.SavedStateHandle
import com.wmt.app.domain.model.ApprovalCustomField
import com.wmt.app.domain.model.ApprovalFieldInput
import com.wmt.app.domain.model.ApprovalFieldOption
import com.wmt.app.domain.model.ApprovalRequestDetail
import com.wmt.app.domain.model.ApprovalRequestForm
import com.wmt.app.testing.FakeApprovalRepository
import com.wmt.app.testing.MainDispatcherRule
import com.wmt.app.testing.approvalRequest
import com.wmt.app.util.AppError
import com.wmt.app.util.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Raising and editing a request.
 *
 * The required-field rule is the part worth pinning down: the server accepts a request
 * with required fields empty, because its validation treats customFieldValues as a
 * nullable array whatever the definition says. If the app does not enforce it, the app
 * becomes the way to file an incomplete request.
 */
class ApprovalFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeApprovalRepository()

    private fun field(
        id: Int,
        name: String = "Amount",
        type: String = "number",
        required: Boolean = false,
        options: List<ApprovalFieldOption> = emptyList(),
        defaultValue: String? = null,
    ) = ApprovalCustomField(
        id = id,
        name = name,
        type = type,
        isRequired = required,
        position = id,
        options = options,
        defaultValue = defaultValue,
    )

    private fun stubForm(vararg fields: ApprovalCustomField) {
        repository.requestFormResponse = Resource.Success(
            ApprovalRequestForm(
                projectId = 4,
                projectName = "Finance",
                projectDescription = null,
                fields = fields.toList(),
            ),
        )
    }

    private fun viewModel(requestId: Int = 0) = ApprovalFormViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(
            mapOf("projectId" to 4, "requestId" to requestId),
        ),
    )

    @Test
    fun `the form loads the fields the project defines`() = runTest {
        stubForm(field(20), field(21, name = "Notes", type = "textarea"))

        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals("Finance", state.projectName)
        assertEquals(listOf("Amount", "Notes"), state.fields.map { it.name })
    }

    @Test
    fun `defaults the project defined are prefilled`() = runTest {
        stubForm(field(20, defaultValue = "100"))

        assertEquals(ApprovalFieldInput.Text("100"), viewModel().state.value.values[20])
    }

    @Test
    fun `a required field left empty blocks submission`() = runTest {
        stubForm(field(20, required = true))
        val vm = viewModel()

        vm.setTitle("Petty cash")
        vm.submit()

        // Nothing was sent, and the field says why.
        assertEquals(0, repository.createCalls)
        assertEquals(setOf(20), vm.state.value.missingFields)
    }

    @Test
    fun `filling the required field clears the complaint and lets it through`() = runTest {
        stubForm(field(20, required = true))
        repository.createResponse = Resource.Success(approvalRequest())
        val vm = viewModel()

        vm.setTitle("Petty cash")
        vm.submit()
        vm.setText(20, "1250")
        vm.submit()

        assertTrue(vm.state.value.missingFields.isEmpty())
        assertEquals(1, repository.createCalls)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `a blank title blocks submission`() = runTest {
        stubForm()
        val vm = viewModel()

        vm.setTitle("   ")
        vm.submit()

        assertEquals(0, repository.createCalls)
        assertEquals("Title is required.", vm.state.value.titleError)
    }

    @Test
    fun `a multi-select is submitted as a list, not a joined string`() = runTest {
        stubForm(
            field(
                id = 7,
                name = "Sites",
                type = "multi_select",
                options = listOf(
                    ApprovalFieldOption(40, "Manila", null),
                    ApprovalFieldOption(42, "Davao", null),
                ),
            ),
        )
        repository.createResponse = Resource.Success(approvalRequest())
        val vm = viewModel()

        vm.setTitle("Site visit")
        vm.toggleOption(7, 40)
        vm.toggleOption(7, 42)
        vm.submit()

        assertEquals(
            ApprovalFieldInput.Selection(listOf(40, 42)),
            repository.createdFieldValues?.get(7),
        )
    }

    @Test
    fun `choosing an already chosen option removes it`() = runTest {
        stubForm(field(7, type = "multi_select"))
        val vm = viewModel()

        vm.toggleOption(7, 40)
        vm.toggleOption(7, 40)

        assertEquals(ApprovalFieldInput.Selection(emptyList()), vm.state.value.values[7])
    }

    @Test
    fun `a formula field is shown but never submitted`() = runTest {
        stubForm(field(20), field(30, name = "Total", type = "formula"))
        repository.createResponse = Resource.Success(approvalRequest())
        val vm = viewModel()

        vm.setTitle("Petty cash")
        vm.setText(30, "whatever")
        vm.submit()

        // The server computes it; sending a value back would be meaningless at best.
        assertFalse(repository.createdFieldValues.orEmpty().containsKey(30))
    }

    @Test
    fun `a required formula field does not block submission`() = runTest {
        // It cannot be filled in, so demanding it would make the form unsubmittable.
        stubForm(field(30, name = "Total", type = "formula", required = true))
        repository.createResponse = Resource.Success(approvalRequest())
        val vm = viewModel()

        vm.setTitle("Petty cash")
        vm.submit()

        assertEquals(1, repository.createCalls)
    }

    @Test
    fun `attachments go with the new request`() = runTest {
        stubForm()
        repository.createResponse = Resource.Success(approvalRequest())
        val vm = viewModel()

        vm.setTitle("Petty cash")
        vm.addAttachments(listOf("content://quote.pdf", "content://receipt.jpg"))
        vm.submit()

        assertEquals(
            listOf("content://quote.pdf", "content://receipt.jpg"),
            repository.createdAttachments,
        )
    }

    @Test
    fun `no more than five attachments are kept`() = runTest {
        stubForm()
        val vm = viewModel()

        vm.addAttachments((1..8).map { "content://file" + it })

        // The server rejects a sixth, so the form never collects one.
        assertEquals(5, vm.state.value.attachments.size)
    }

    @Test
    fun `the same file picked twice is only attached once`() = runTest {
        stubForm()
        val vm = viewModel()

        vm.addAttachments(listOf("content://quote.pdf"))
        vm.addAttachments(listOf("content://quote.pdf"))

        assertEquals(1, vm.state.value.attachments.size)
    }

    @Test
    fun `a 422 is shown against the field the server named`() = runTest {
        stubForm(field(20))
        repository.createResponse = Resource.Error(
            AppError.Validation(
                fieldErrors = mapOf("customFieldValues.20" to listOf("Must be a number.")),
                summary = "The given data was invalid.",
            ),
        )
        val vm = viewModel()

        vm.setTitle("Petty cash")
        vm.submit()

        val state = vm.state.value
        assertFalse(state.saved)
        assertEquals(listOf("Must be a number."), state.fieldErrors["customFieldValues.20"])
        assertEquals("The given data was invalid.", state.message)
    }

    @Test
    fun `editing loads the record and saves through update`() = runTest {
        stubForm(field(20))
        repository.detailResponses = mutableListOf(
            Resource.Success(
                ApprovalRequestDetail(
                    request = approvalRequest(title = "Existing", description = "Old text"),
                    canDecide = false,
                    canEdit = true,
                ),
            ),
        )
        repository.updateResponse = Resource.Success(approvalRequest())
        val vm = viewModel(requestId = 12)

        assertEquals("Existing", vm.state.value.title)
        assertEquals("Old text", vm.state.value.description)

        vm.setTitle("Revised")
        vm.submit()

        assertEquals(1, repository.updateCalls)
        assertEquals(0, repository.createCalls)
        assertEquals("Revised", repository.createdTitle)
    }

    @Test
    fun `a failed form load becomes the screen`() = runTest {
        repository.requestFormResponse = Resource.Error(AppError.NoConnection)

        val state = viewModel().state.value

        assertEquals("No connection.", state.error)
        assertNull(state.titleError)
    }
}
