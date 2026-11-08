package com.wmt.app.ui.notes

/**
 * A note's body as the phone edits it: plain text, one paragraph per line.
 *
 * The web edits notes in a rich-text editor (bold, italic, lists, links, headings), and
 * the phone has no rich-text editor. Editing a formatted note as text and saving it would
 * strip every one of those marks without a word, so the phone edits only notes that have
 * no formatting to lose -- paragraphs and line breaks -- and shows the rest read-only.
 * That is what [isPlain] decides, and the round trip through [toText] and [toHtml] must be
 * exact for anything it admits: opening and saving an untouched note changes nothing.
 */
object NoteText {

    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>")
    private val PLAIN_TAGS = setOf("p", "br")

    /** True when every tag in [html] is a paragraph or a line break, so nothing would be lost. */
    fun isPlain(html: String?): Boolean {
        if (html.isNullOrBlank()) return true
        return TAG.findAll(html).all { it.groupValues[2].lowercase() in PLAIN_TAGS }
    }

    /** The stored body as editable text: a line per paragraph, a line break as a new line. */
    fun toText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        // No tags at all is plain text already (a note the API stored without markup).
        if (!TAG.containsMatchIn(html)) return decode(html)
        val text = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>\\s*<p[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</?p[^>]*>", RegexOption.IGNORE_CASE), "")
        return decode(text)
    }

    /** Text back into the markup the web editor writes: one `<p>` per line, empty lines kept. */
    fun toHtml(text: String): String {
        if (text.isEmpty()) return ""
        return text.split("\n").joinToString("") { line -> "<p>${encode(line)}</p>" }
    }

    private fun encode(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        // The web editor writes a non-breaking space as &nbsp;; written back as the raw
        // character, an untouched note would come back different from how it went in.
        .replace("\u00A0", "&nbsp;")

    private fun decode(s: String) = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        // Last, so "&amp;lt;" decodes to the literal "&lt;" someone typed, not to "<".
        .replace("&amp;", "&")
}
