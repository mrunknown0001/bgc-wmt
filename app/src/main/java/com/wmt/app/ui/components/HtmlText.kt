package com.wmt.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow

private val MENTION_SPAN = Regex(
    "<span\\b[^>]*class=\"mention\"[^>]*>(.*?)</span>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

/**
 * Renders an HTML string (as produced by the WMT web editor — `<p>`, `<strong>`,
 * `<br>`, mention `<span>`s, etc.) as styled text instead of showing raw tags.
 * Mention spans (which carry only a CSS class the parser ignores) are rewritten to
 * an inline color+weight style so they render highlighted.
 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val mentionColor = MaterialTheme.colorScheme.primary
    val annotated = remember(html, mentionColor) {
        val hex = "#%06X".format(0xFFFFFF and mentionColor.toArgb())
        val styled = MENTION_SPAN.replace(html) { match ->
            "<span style=\"color:$hex;font-weight:bold;\">${match.groupValues[1]}</span>"
        }
        AnnotatedString.fromHtml(styled)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}
