package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ---------------------------------------------------------------------------
// WCAG 2.1 contrast maths.
//
// This lives in the main source set rather than the tests because the accent
// palettes are *derived* against it at runtime -- a base colour is moved in
// lightness until it clears the bar -- so the derivation and the test that
// polices it have to agree on the arithmetic by construction.
// ---------------------------------------------------------------------------

/** WCAG relative luminance of an opaque sRGB colour. */
fun Color.wcagLuminance(): Float {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** WCAG contrast ratio between two opaque colours: 1.0 (identical) to 21.0 (black on white). */
fun contrastRatio(foreground: Color, background: Color): Float {
    val a = foreground.wcagLuminance()
    val b = background.wcagLuminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/**
 * Flattens a translucent colour onto [background].
 *
 * Badge containers are tints carrying an alpha, and an alpha-composited colour
 * has no fixed contrast ratio until you know what it sits on -- so measuring
 * one honestly means compositing it first, against the surface of the theme
 * actually in use.
 */
fun Color.compositeOver(background: Color): Color {
    if (alpha >= 1f) return this
    return Color(
        red = red * alpha + background.red * (1f - alpha),
        green = green * alpha + background.green * (1f - alpha),
        blue = blue * alpha + background.blue * (1f - alpha),
        alpha = 1f,
    )
}
