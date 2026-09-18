package com.wmt.app.data.repository

import com.wmt.app.domain.model.ApprovalFieldInput
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How custom field values are encoded for the two endpoints that accept them.
 *
 * The shapes are not interchangeable. The server stores multi_select and people as JSON
 * arrays and coerces everything else to a single column, so a list sent as a scalar is
 * stored as one option with a nonsense id -- a corruption that succeeds quietly rather
 * than failing.
 */
class ApprovalFieldInputTest {

    private fun partText(body: okhttp3.RequestBody): String {
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun `a scalar goes as one multipart field keyed by field id`() {
        val parts = mapOf(20 to ApprovalFieldInput.Text("1250.5")).toPartMap()

        assertEquals(setOf("customFieldValues[20]"), parts.keys)
        assertEquals("1250.5", partText(parts.getValue("customFieldValues[20]")))
    }

    @Test
    fun `a list goes as indexed multipart keys, which PHP reads back as an array`() {
        // Multipart cannot repeat a key, so the index is what makes this an array.
        val parts = mapOf(7 to ApprovalFieldInput.Selection(listOf(40, 42))).toPartMap()

        assertEquals(
            setOf("customFieldValues[7][0]", "customFieldValues[7][1]"),
            parts.keys,
        )
        assertEquals("40", partText(parts.getValue("customFieldValues[7][0]")))
        assertEquals("42", partText(parts.getValue("customFieldValues[7][1]")))
    }

    @Test
    fun `a cleared field is sent empty rather than dropped`() {
        // Omitting it would leave the old value in place; the server clears on empty.
        val parts = mapOf(20 to ApprovalFieldInput.Text(null)).toPartMap()

        assertEquals(setOf("customFieldValues[20]"), parts.keys)
        assertEquals("", partText(parts.getValue("customFieldValues[20]")))
    }

    @Test
    fun `an empty selection sends no parts for that field`() {
        val parts = mapOf(7 to ApprovalFieldInput.Selection(emptyList())).toPartMap()

        assertTrue(parts.isEmpty())
    }

    @Test
    fun `several fields keep their own keys`() {
        val parts = mapOf(
            1 to ApprovalFieldInput.Text("hello"),
            2 to ApprovalFieldInput.Selection(listOf(9)),
        ).toPartMap()

        assertEquals(
            setOf("customFieldValues[1]", "customFieldValues[2][0]"),
            parts.keys,
        )
    }

    @Test
    fun `the json form keeps lists as lists and ids as numbers`() {
        val json = mapOf(
            20 to ApprovalFieldInput.Text("1250.5"),
            7 to ApprovalFieldInput.Selection(listOf(40, 42)),
        ).toJsonValues()

        assertEquals("1250.5", json["20"])
        // Numbers, not strings, so the stored JSON matches what the web app writes.
        assertEquals(listOf(40, 42), json["7"])
    }

    @Test
    fun `the json form sends null for a cleared field`() {
        val json = mapOf(20 to ApprovalFieldInput.Text(null)).toJsonValues()

        assertTrue(json.containsKey("20"))
        assertEquals(null, json["20"])
    }
}
