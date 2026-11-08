package com.wmt.app.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which notes the phone may edit, and that editing one loses nothing.
 *
 * The note bodies here are what the web's TipTap editor writes. The one rule that must
 * never break: a note admitted by isPlain() comes back from toText() then toHtml()
 * exactly as it went in, so opening a note on the phone and saving it is a no-op.
 */
class NoteTextTest {

    @Test
    fun `paragraphs and line breaks are plain, anything else is formatting`() {
        assertTrue(NoteText.isPlain(null))
        assertTrue(NoteText.isPlain(""))
        assertTrue(NoteText.isPlain("<p>Bring the ladder.</p><p>And gloves.</p>"))
        assertTrue(NoteText.isPlain("<p>one<br>two</p>"))
        assertTrue(NoteText.isPlain("Just text, no markup"))

        assertFalse(NoteText.isPlain("<p><strong>Bold</strong></p>"))
        assertFalse(NoteText.isPlain("<ul><li><p>one</p></li></ul>"))
        assertFalse(NoteText.isPlain("<h2>Agenda</h2>"))
        assertFalse(NoteText.isPlain("""<p><a href="https://example.com">link</a></p>"""))
        assertFalse(NoteText.isPlain("<p><em>i</em> <u>u</u> <s>s</s></p>"))
    }

    @Test
    fun `a plain note survives being opened and saved unchanged`() {
        listOf(
            "<p>Bring the ladder.</p><p>And gloves.</p>",
            "<p>Tom &amp; Jerry &lt;3</p>",
            "<p>first</p><p></p><p>after a blank line</p>",
            "<p>single</p>",
            "<p>two&nbsp; spaces kept</p>",
        ).forEach { html ->
            assertTrue(html, NoteText.isPlain(html))
            assertEquals(html, NoteText.toHtml(NoteText.toText(html)))
        }
    }

    @Test
    fun `text becomes one paragraph per line, escaped`() {
        assertEquals("<p>a &lt;b&gt; &amp; c</p><p></p><p>d</p>", NoteText.toHtml("a <b> & c\n\nd"))
        assertEquals("", NoteText.toHtml(""))
    }

    @Test
    fun `reading a body turns paragraphs and breaks into lines and decodes entities`() {
        assertEquals("one\ntwo\nthree", NoteText.toText("<p>one<br>two</p><p>three</p>"))
        assertEquals("Tom & Jerry <3", NoteText.toText("<p>Tom &amp; Jerry &lt;3</p>"))
        // Someone who typed the characters "&lt;" gets them back, not a "<".
        assertEquals("&lt;", NoteText.toText("<p>&amp;lt;</p>"))
    }

    @Test
    fun `typed angle brackets are text, not markup`() {
        val html = NoteText.toHtml("if a < b then <script>")

        assertTrue(NoteText.isPlain(html))
        assertEquals("if a < b then <script>", NoteText.toText(html))
    }
}
