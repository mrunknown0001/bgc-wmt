package com.wmt.app.ui.common

import com.wmt.app.util.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which refusals a form has to say out loud.
 *
 * Two screens got this wrong the same way: they read "there are field errors" as "a
 * field is showing them", so a 422 keyed on anything the form does not render went
 * nowhere -- no snackbar, no field, a button that looked broken. The rule lives in one
 * place now, and these are the cases that made it necessary.
 */
class RefusalTest {

    private fun validation(vararg pairs: Pair<String, String>) = AppError.Validation(
        fieldErrors = pairs.associate { (key, message) -> key to listOf(message) },
        summary = "The given data was invalid.",
    )

    @Test
    fun `an error the form shows in place is not repeated`() {
        val refusal = refusalToAnnounce(
            validation("ends_on" to "Must be after the start date."),
            shownKeys = setOf("delegate_id", "starts_on", "ends_on"),
        )

        assertNull(refusal)
    }

    @Test
    fun `an error on a key the form does not render is announced`() {
        val refusal = refusalToAnnounce(
            validation("cover" to "That overlaps cover you already have."),
            shownKeys = setOf("delegate_id", "starts_on", "ends_on"),
        )

        assertEquals("That overlaps cover you already have.", refusal)
    }

    @Test
    fun `a locked custom field is a key the editor never shows`() {
        // The editor leaves out fields locked for this person, so values.12 has no field.
        val refusal = refusalToAnnounce(
            validation("values.12" to "This field is locked."),
            shownKeys = setOf("values.7", "values.9"),
        )

        assertEquals("This field is locked.", refusal)
    }

    @Test
    fun `a mix is announced for the part that has nowhere to go`() {
        val refusal = refusalToAnnounce(
            validation(
                "starts_on" to "Not a date.",
                "cover" to "That overlaps cover you already have.",
            ),
            shownKeys = setOf("starts_on"),
        )

        assertEquals("That overlaps cover you already have.", refusal)
    }

    @Test
    fun `a validation error naming no fields falls back to its summary`() {
        val refusal = refusalToAnnounce(
            AppError.Validation(fieldErrors = emptyMap(), summary = "That cover overlaps."),
            shownKeys = setOf("starts_on"),
        )

        assertEquals("That cover overlaps.", refusal)
    }

    @Test
    fun `anything that is not a validation error is announced as it stands`() {
        assertEquals(
            "You may not arrange cover.",
            refusalToAnnounce(AppError.Forbidden("You may not arrange cover."), setOf("starts_on")),
        )
        assertEquals(
            AppError.Unauthorized.message,
            refusalToAnnounce(AppError.Unauthorized, setOf("starts_on")),
        )
    }

    @Test
    fun `a form that renders nothing in place announces everything`() {
        val refusal = refusalToAnnounce(validation("title" to "Required."), shownKeys = emptySet())

        assertEquals("Required.", refusal)
    }
}
