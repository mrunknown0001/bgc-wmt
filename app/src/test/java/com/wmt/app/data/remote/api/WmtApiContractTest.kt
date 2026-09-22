package com.wmt.app.data.remote.api

import com.squareup.moshi.Moshi
import com.wmt.app.data.remote.AttachmentPartFactory
import com.wmt.app.data.remote.dto.ApprovalAdvanceRequest
import com.wmt.app.data.remote.dto.AddTimeLogRequest
import com.wmt.app.data.remote.dto.AmendTimeLogRequest
import com.wmt.app.data.remote.dto.NotificationPreferenceRequest
import com.wmt.app.data.remote.dto.PauseTaskRequest
import com.wmt.app.data.remote.dto.ReviewAmendmentRequest
import com.wmt.app.data.remote.dto.UpdateApprovalItemRequest
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Checks the shape of the requests [WmtApi] actually sends.
 *
 * Retrofit resolves its annotations when a method is first called, not at compile time,
 * so a wrong path, verb or part name compiles cleanly and fails only on a device. Here
 * the interface is built with validateEagerly, which forces every declaration (and its
 * converter) to resolve up front, and each call is answered by an interceptor that
 * records the request instead of sending it. The paths asserted below are the ones in
 * the deployed routes/api.php.
 */
class WmtApiContractTest {

    private val sent = mutableListOf<Request>()
    private lateinit var api: WmtApi

    @Before
    fun setUp() {
        val recorder = Interceptor { chain ->
            val request = chain.request()
            sent += request
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                // Every response DTO these calls decode into defaults fully, so an empty
                // object is enough to get past conversion.
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        api = Retrofit.Builder()
            .baseUrl(BASE)
            .client(OkHttpClient.Builder().addInterceptor(recorder).build())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            // Resolves all 60-odd declarations now, converters included.
            .validateEagerly(true)
            .build()
            .create(WmtApi::class.java)
    }

    private val last: Request get() = sent.last()

    private fun assertSent(method: String, pathAndQuery: String) {
        assertEquals(method, last.method)
        assertEquals(BASE.dropLast(1) + pathAndQuery, last.url.toString())
    }

    /** The multipart body, read back as text so part names can be asserted. */
    private fun lastBodyText(): String {
        val buffer = Buffer()
        last.body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun `building the interface resolves every declaration`() {
        // setUp already called create() with validateEagerly; reaching here means every
        // method annotation and return-type converter resolved.
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `token refresh posts to the route that mints a replacement`() = runTest {
        api.refreshToken()

        assertSent("POST", "/api/token/refresh")
    }

    @Test
    fun `the badge count is a plain get`() = runTest {
        api.approvalCounts()

        assertSent("GET", "/api/approvals/counts")
    }

    @Test
    fun `queue and trail carry their filters as query parameters`() = runTest {
        api.myApprovals(page = 2, search = "petty")
        assertSent("GET", "/api/my-approvals?page=2&search=petty")

        api.approvalTrail(page = 1, decision = "approved", projectId = 4, search = "cash")
        assertSent("GET", "/api/my-approvals/trail?page=1&decision=approved&project_id=4&search=cash")

        api.myRequests(page = 3, status = "changes_requested", projectId = 7)
        assertSent("GET", "/api/my-requests?page=3&status=changes_requested&approval_project_id=7")
    }

    @Test
    fun `omitted filters are left out of the query entirely`() = runTest {
        api.myApprovals(page = 1)

        assertSent("GET", "/api/my-approvals?page=1")
        assertNull("a null search must not become an empty parameter", last.url.queryParameter("search"))
    }


    @Test
    fun `project reads hit the read-only routes, with available before the wildcard`() = runTest {
        api.approvalProjects(page = 1)
        assertSent("GET", "/api/approval-projects?page=1")

        // Distinct path, not an id: the server registers it ahead of {approvalProject}.
        api.availableApprovalProjects()
        assertSent("GET", "/api/approval-projects/available")

        api.approvalProject(4)
        assertSent("GET", "/api/approval-projects/4")

        api.approvalRequestForm(4)
        assertSent("GET", "/api/approval-projects/4/request-form")
    }

    @Test
    fun `archived is sent only when asked for`() = runTest {
        api.approvalProjects(page = 1, archived = true)
        assertEquals("true", last.url.queryParameter("archived"))

        api.approvalProjects(page = 1, archived = null)
        assertNull("the default list must not pin archived=false", last.url.queryParameter("archived"))
    }

    @Test
    fun `item reads and the section filter nest under the project`() = runTest {
        api.approvalItems(projectId = 4, page = 1, status = "pending", sectionId = "none")
        assertSent("GET", "/api/approval-projects/4/items?page=1&status=pending&section_id=none")

        api.approvalItem(projectId = 4, itemId = 12)
        assertSent("GET", "/api/approval-projects/4/items/12")

        api.approvalItemComments(projectId = 4, itemId = 12, page = 2)
        assertSent("GET", "/api/approval-projects/4/items/12/comments?page=2")
    }

    @Test
    fun `the decision posts action and comment to advance`() = runTest {
        api.advanceApprovalItem(
            projectId = 4,
            itemId = 12,
            body = ApprovalAdvanceRequest(action = "rejected", comment = "Needs a quote"),
        )

        assertSent("POST", "/api/approval-projects/4/items/12/advance")
        val body = lastBodyText()
        assertTrue(body, body.contains("\"action\":\"rejected\""))
        assertTrue(body, body.contains("\"comment\":\"Needs a quote\""))
    }

    @Test
    fun `resubmit and cancel use the verbs the routes expose`() = runTest {
        api.resubmitApprovalItem(projectId = 4, itemId = 12)
        assertSent("POST", "/api/approval-projects/4/items/12/resubmit")

        api.cancelApprovalItem(projectId = 4, itemId = 12)
        assertSent("DELETE", "/api/approval-projects/4/items/12")
    }

    @Test
    fun `updating a request puts json with field values keyed by field id`() = runTest {
        api.updateApprovalItem(
            projectId = 4,
            itemId = 12,
            body = UpdateApprovalItemRequest(
                title = "Petty cash",
                description = null,
                customFieldValues = mapOf("20" to "1250.5"),
            ),
        )

        assertSent("PUT", "/api/approval-projects/4/items/12")
        val body = lastBodyText()
        assertTrue(body, body.contains("\"customFieldValues\":{\"20\":\"1250.5\"}"))
    }

    @Test
    fun `creating a request sends multipart parts under the names the server reads`() = runTest {
        api.createApprovalItem(
            projectId = 4,
            title = textPart("Petty cash"),
            description = textPart("For the site office"),
            sectionId = textPart("8"),
            customFieldValues = mapOf("customFieldValues[20]" to textPart("1250.5")),
            attachments = emptyList(),
        )

        assertSent("POST", "/api/approval-projects/4/items")
        assertTrue(last.body?.contentType()?.type == "multipart")
        val body = lastBodyText()
        assertTrue(body, body.contains("name=\"title\""))
        assertTrue(body, body.contains("name=\"description\""))
        assertTrue(body, body.contains("name=\"approval_section_id\""))
        // The bracketed key is what Laravel reads back as customFieldValues.20.
        assertTrue(body, body.contains("name=\"customFieldValues[20]\""))
        assertTrue(body, body.contains("Petty cash"))
    }

    @Test
    fun `an optional multipart part is omitted rather than sent empty`() = runTest {
        api.createApprovalItem(
            projectId = 4,
            title = textPart("No section"),
            description = null,
            sectionId = null,
            customFieldValues = emptyMap(),
            attachments = emptyList(),
        )

        val body = lastBodyText()
        assertTrue(body, body.contains("name=\"title\""))
        assertTrue("a null section must not be sent", !body.contains("approval_section_id"))
        assertTrue("a null description must not be sent", !body.contains("name=\"description\""))
    }

    @Test
    fun `a comment posts its body as a multipart field`() = runTest {
        api.addApprovalItemComment(
            projectId = 4,
            itemId = 12,
            body = textPart("Looks fine"),
            attachments = emptyList(),
        )

        assertSent("POST", "/api/approval-projects/4/items/12/comments")
        val body = lastBodyText()
        assertTrue(body, body.contains("name=\"body\""))
        assertTrue(body, body.contains("Looks fine"))
    }

    @Test
    fun `the clock routes use the verbs the server exposes`() = runTest {
        // PATCH rather than POST: these move a task between states rather than
        // creating anything, and the server registers them that way.
        api.startTaskClock(projectId = 4, taskId = 12)
        assertSent("PATCH", "/api/projects/4/tasks/12/start")

        api.resumeTaskClock(projectId = 4, taskId = 12)
        assertSent("PATCH", "/api/projects/4/tasks/12/resume")

        // The preview is a read, so it is a GET while its siblings are PATCH.
        api.taskPausePreview(projectId = 4, taskId = 12)
        assertSent("GET", "/api/projects/4/tasks/12/pause-preview")
    }

    @Test
    fun `pausing sends the minutes and the note as json`() = runTest {
        api.pauseTaskClock(
            projectId = 4,
            taskId = 12,
            body = PauseTaskRequest(minutes = 95, note = "Site visit"),
        )

        assertSent("PATCH", "/api/projects/4/tasks/12/pause")
        val body = lastBodyText()
        assertTrue(body, body.contains("\"minutes\":95"))
        assertTrue(body, body.contains("\"note\":\"Site visit\""))
    }

    @Test
    fun `the timesheet reads and deletes by the ids the routes expect`() = runTest {
        // Task-scoped to read, log-scoped to delete: they are different resources.
        api.taskTimeLogs(taskId = 12)
        assertSent("GET", "/api/tasks/12/time-logs")

        api.deleteTimeLog(timeLogId = 77)
        assertSent("DELETE", "/api/time-logs/77")
    }

    @Test
    fun `a correction sends the duration as typed, not as minutes`() = runTest {
        api.amendTimeLog(
            timeLogId = 77,
            body = AmendTimeLogRequest(duration = "1:30", reason = "Clock left running"),
        )

        assertSent("POST", "/api/time-logs/77/amendments")
        val body = lastBodyText()
        // The server parses 1.5, 1:30 and 90m alike; converting here would lose what
        // the person actually wrote.
        assertTrue(body, body.contains("\"duration\":\"1:30\""))
        assertTrue(body, body.contains("\"reason\":\"Clock left running\""))
    }

    @Test
    fun `asking for an entry on a past day hangs off the task, not a log`() = runTest {
        api.addTimeLogEntry(
            taskId = 12,
            body = AddTimeLogRequest(
                duration = "90m",
                loggedOn = "2026-09-17",
                reason = "Worked offline",
            ),
        )

        // There is no entry to attach it to yet, which is the whole point of the ask.
        assertSent("POST", "/api/tasks/12/time-log-amendments")
        val body = lastBodyText()
        assertTrue(body, body.contains("\"logged_on\":\"2026-09-17\""))
    }

    @Test
    fun `deciding a correction posts to the matching route`() = runTest {
        api.approveAmendment(amendmentId = 5, body = ReviewAmendmentRequest(note = "Agreed"))
        assertSent("POST", "/api/time-log-amendments/5/approve")
        assertTrue(lastBodyText().contains("\"note\":\"Agreed\""))

        api.rejectAmendment(amendmentId = 5, body = ReviewAmendmentRequest(note = null))
        assertSent("POST", "/api/time-log-amendments/5/reject")
    }

    @Test
    fun `notification preferences read and write one switch at a time`() = runTest {
        api.notificationPreferences()
        // Not shadowed by web.php, unlike /api/search — this literal path is the one
        // that answered on the deployed server.
        assertSent("GET", "/api/notification-preferences")

        api.updateNotificationPreference(
            NotificationPreferenceRequest(type = "email_task_assigned", enabled = false),
        )
        assertSent("POST", "/api/notification-preferences")
        val body = lastBodyText()
        assertTrue(body, body.contains("\"type\":\"email_task_assigned\""))
        assertTrue(body, body.contains("\"enabled\":false"))
    }

    private fun textPart(value: String) = AttachmentPartFactory.textPart(value)

    companion object {
        private const val BASE = "https://wmt-dev.bfcgroup.ph/"
    }
}
