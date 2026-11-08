package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// ---------------------------------------------------------------------------
// The eight accent palettes, chosen on the server and followed by the app.
// ---------------------------------------------------------------------------

/**
 * An accent WMT web can be set to, identified by the key the server sends in
 * `primary_color` and the solid mid-tone it stands for.
 *
 * [base] is the badge-and-identity mid-tone, *not* Material's `primary`. Used
 * directly as `primary` only Purple clears WCAG AA against a white surface,
 * and Blue, Red and Indigo sit at the mid-luminance where neither white nor
 * black text reaches 4.5:1 on them -- no choice of `onPrimary` rescues those.
 * So each scheme's `primary` is derived from [base] by moving lightness until
 * it clears the bar (see [accentColors]), while [base] itself stays exactly as
 * given, for badges and project dots where nothing is written on top.
 */
enum class Accent(val key: String, val base: Color) {
    Blue("blue", Color(0xFF3E6FF4)),
    Green("green", Color(0xFF23A45C)),
    Amber("amber", Color(0xFFE0A100)),
    Red("red", Color(0xFFE53935)),
    Teal("teal", Color(0xFF14B8A6)),
    Indigo("indigo", Color(0xFF6366F1)),
    Orange("orange", Color(0xFFF97316)),
    Purple("purple", Color(0xFF8A4DDB)),
    ;

    companion object {
        /** What the app wears before it has ever heard from a server. */
        val Default = Blue

        /**
         * Resolves whatever `primary_color` carried.
         *
         * The field is an undocumented nullable string, so both plausible
         * shapes are accepted: a key like "blue", or a hex like "#3E6FF4". A
         * hex we do not recognise picks the nearest of the eight rather than
         * giving up, and anything we cannot read at all -- null, blank, a
         * colour name we do not carry -- falls back to [Default].
         */
        fun from(raw: String?): Accent {
            val value = raw?.trim()?.lowercase().orEmpty()
            if (value.isEmpty()) return Default
            entries.firstOrNull { it.key == value }?.let { return it }
            val hex = parseHex(value) ?: return Default
            return entries.minByOrNull { perceptualDistance(hex, it.base) } ?: Default
        }
    }
}

/** `#rgb`, `#rrggbb` or CSS `#rrggbbaa` (alpha ignored), with or without the hash. */
private fun parseHex(value: String): Color? {
    val digits = value.removePrefix("#")
    if (digits.any { it !in "0123456789abcdef" }) return null
    val rgb = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("")
        6, 8 -> digits.take(6)
        else -> return null
    }
    return Color(0xFF000000L or rgb.toLong(16))
}

/**
 * "Redmean" weighted distance -- cheap, and closer to how the eye groups hues
 * than a plain RGB difference, which is all a nearest-of-eight match needs.
 */
private fun perceptualDistance(a: Color, b: Color): Float {
    val meanRed = (a.red + b.red) / 2f
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return (2f + meanRed) * dr * dr + 4f * dg * dg + (3f - meanRed) * db * db
}

// ---------------------------------------------------------------------------
// Derivation
// ---------------------------------------------------------------------------

/**
 * The ratio the derivation aims for. A little above the 4.5:1 AA bar so that
 * rounding, or a later change to the neutral surfaces, cannot quietly push a
 * pair underneath it.
 */
const val ACCENT_CONTRAST_TARGET = 4.6f

/** The accent-driven slots of a Material colour scheme, ready to hand over. */
data class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

/**
 * Derives one accent's scheme slots for a theme, against the [surface] the
 * accent will actually be read on -- passed in rather than assumed, so the
 * neutrals can change without silently invalidating the contrast guarantees.
 *
 * Hue and saturation carry over from [Accent.base] untouched; only lightness
 * moves, which is what keeps Amber recognisably amber even once it is dark
 * enough to write white text on. Secondary and tertiary stay in the accent's
 * own hue too -- a third of the chroma, and a further step of lightness -- so
 * one server setting drives the whole scheme rather than a second brand
 * colour being invented beside it.
 */
fun Accent.accentColors(dark: Boolean, surface: Color): AccentColors {
    val hsl = base.toHsl()
    val muted = hsl.copy(s = hsl.s * 0.35f)
    return if (dark) {
        val primary = hsl.lightenUntilReadable(on = surface)
        val secondary = muted.lightenUntilReadable(on = surface)
        // Tertiary is a further step along the accent's own hue, not a
        // rotation of it. Material's usual +60 degrees sends blue, indigo and
        // purple into magenta, which this design does not allow anywhere --
        // and blue is the default, so the stat tiles on a fresh install were
        // the first thing to show it.
        val deep = hsl.copy(l = (primary.toHsl().l + 0.14f).coerceAtMost(0.95f))
        // The container is darkened to suit its content rather than pinned to
        // a fixed lightness. A pale tint of a high-luminance hue -- Amber
        // especially -- cannot reach the bar against any container still light
        // enough to read as tinted, so here the container gives way instead of
        // the text being driven to white.
        val onPrimaryBox = hsl.copy(l = 0.90f).toColor()
        val onSecondaryBox = muted.copy(l = 0.90f).toColor()
        val onTertiaryBox = deep.copy(l = 0.90f).toColor()
        AccentColors(
            primary = primary,
            onPrimary = hsl.darkenUntilReadable(on = primary),
            primaryContainer = hsl.darkenUntilReadable(on = onPrimaryBox),
            onPrimaryContainer = onPrimaryBox,
            secondary = secondary,
            onSecondary = muted.darkenUntilReadable(on = secondary),
            secondaryContainer = muted.darkenUntilReadable(on = onSecondaryBox),
            onSecondaryContainer = onSecondaryBox,
            tertiary = deep.toColor(),
            onTertiary = deep.darkenUntilReadable(on = deep.toColor()),
            tertiaryContainer = deep.darkenUntilReadable(on = onTertiaryBox),
            onTertiaryContainer = onTertiaryBox,
        )
    } else {
        val primary = hsl.darkenUntilReadable(on = surface)
        // The same step as in dark, taken the other way: darker than primary,
        // so it still clears the bar on a light surface without a hue change.
        val deep = hsl.copy(l = (primary.toHsl().l - 0.14f).coerceAtLeast(0.08f))
        val primaryBox = hsl.copy(l = 0.93f).toColor()
        val secondaryBox = muted.copy(l = 0.93f).toColor()
        val tertiaryBox = deep.copy(l = 0.88f).toColor()
        AccentColors(
            primary = primary,
            // White is safe by construction: `primary` was darkened until it
            // cleared the bar against `surface`, and white is no darker than
            // any surface we put it on.
            onPrimary = Color.White,
            primaryContainer = primaryBox,
            onPrimaryContainer = hsl.darkenUntilReadable(on = primaryBox),
            secondary = muted.darkenUntilReadable(on = surface),
            onSecondary = Color.White,
            secondaryContainer = secondaryBox,
            onSecondaryContainer = muted.darkenUntilReadable(on = secondaryBox),
            tertiary = deep.toColor(),
            onTertiary = Color.White,
            tertiaryContainer = tertiaryBox,
            onTertiaryContainer = deep.darkenUntilReadable(on = tertiaryBox),
        )
    }
}

// --- HSL, so that only lightness moves -------------------------------------

internal data class Hsl(val h: Float, val s: Float, val l: Float)

internal fun Color.toHsl(): Hsl {
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    val span = high - low
    val l = (high + low) / 2f
    if (span == 0f) return Hsl(0f, 0f, l)
    val s = span / (1f - abs(2f * l - 1f))
    val h = when (high) {
        red -> 60f * (((green - blue) / span) % 6f)
        green -> 60f * (((blue - red) / span) + 2f)
        else -> 60f * (((red - green) / span) + 4f)
    }
    return Hsl((h + 360f) % 360f, s, l)
}

internal fun Hsl.toColor(): Color {
    val chroma = (1f - abs(2f * l - 1f)) * s
    val sextant = h / 60f
    val mid = chroma * (1f - abs((sextant % 2f) - 1f))
    val (r, g, b) = when (sextant.toInt()) {
        0 -> Triple(chroma, mid, 0f)
        1 -> Triple(mid, chroma, 0f)
        2 -> Triple(0f, chroma, mid)
        3 -> Triple(0f, mid, chroma)
        4 -> Triple(mid, 0f, chroma)
        else -> Triple(chroma, 0f, mid)
    }
    val base = l - chroma / 2f
    return Color(
        (r + base).coerceIn(0f, 1f),
        (g + base).coerceIn(0f, 1f),
        (b + base).coerceIn(0f, 1f),
    )
}

/**
 * Walks lightness down until the colour clears [ACCENT_CONTRAST_TARGET]
 * against [on].
 *
 * Darkening only ever raises contrast with a light surface, so the walk is
 * monotonic and stops at the first value that passes -- the lightest shade
 * that is still readable, which keeps as much of the original colour as the
 * bar allows.
 */
internal fun Hsl.darkenUntilReadable(on: Color): Color =
    walkUntilReadable(on, step = -0.01f, limit = 0.04f)

/** The same walk upward, for an accent that has to read on a dark surface. */
internal fun Hsl.lightenUntilReadable(on: Color): Color =
    walkUntilReadable(on, step = 0.01f, limit = 0.97f)

private fun Hsl.walkUntilReadable(on: Color, step: Float, limit: Float): Color {
    var lightness = l
    var candidate = toColor()
    while (contrastRatio(candidate, on) < ACCENT_CONTRAST_TARGET) {
        lightness += step
        val past = if (step < 0f) lightness < limit else lightness > limit
        if (past) return copy(l = limit).toColor()
        candidate = copy(l = lightness).toColor()
    }
    return candidate
}
