package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Polices WCAG AA across all eight accents, in both themes.
 *
 * This is not a formality. The eight base mid-tones do not clear 4.5:1 as
 * `primary` on their own -- seven of them fail against a white surface, and
 * Blue, Red and Indigo fail against white *and* black text, so no choice of
 * `onPrimary` would save them. The schemes are therefore derived rather than
 * declared, and a derivation is only trustworthy if something measures it: a
 * later tweak to a base hex, a neutral, or the walk itself has to fail here
 * rather than ship an unreadable button.
 */
class AccentContrastTest {

    /** The WCAG AA bar for normal-weight text. */
    private val aa = 4.5f

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label is %.2f:1, below the %.1f:1 AA bar".format(ratio, aa),
            ratio >= aa,
        )
    }

    @Test
    fun `onPrimary is readable on primary for every accent in both themes`() {
        for (accent in Accent.entries) {
            for (dark in listOf(false, true)) {
                val surface = if (dark) SurfaceDark else Surface
                val colors = accent.accentColors(dark = dark, surface = surface)
                assertReadable(
                    "${accent.key} onPrimary on primary (${theme(dark)})",
                    colors.onPrimary,
                    colors.primary,
                )
            }
        }
    }

    @Test
    fun `primary is readable on surface for every accent in both themes`() {
        for (accent in Accent.entries) {
            for (dark in listOf(false, true)) {
                val surface = if (dark) SurfaceDark else Surface
                val colors = accent.accentColors(dark = dark, surface = surface)
                assertReadable(
                    "${accent.key} primary on surface (${theme(dark)})",
                    colors.primary,
                    surface,
                )
            }
        }
    }

    @Test
    fun `container content is readable on its container for every accent`() {
        for (accent in Accent.entries) {
            for (dark in listOf(false, true)) {
                val surface = if (dark) SurfaceDark else Surface
                val c = accent.accentColors(dark = dark, surface = surface)
                val where = theme(dark)
                assertReadable("${accent.key} onPrimaryContainer ($where)", c.onPrimaryContainer, c.primaryContainer)
                assertReadable("${accent.key} onSecondaryContainer ($where)", c.onSecondaryContainer, c.secondaryContainer)
                assertReadable("${accent.key} onTertiaryContainer ($where)", c.onTertiaryContainer, c.tertiaryContainer)
            }
        }
    }

    @Test
    fun `secondary and tertiary are readable on surface for every accent`() {
        for (accent in Accent.entries) {
            for (dark in listOf(false, true)) {
                val surface = if (dark) SurfaceDark else Surface
                val c = accent.accentColors(dark = dark, surface = surface)
                val where = theme(dark)
                assertReadable("${accent.key} secondary on surface ($where)", c.secondary, surface)
                assertReadable("${accent.key} tertiary on surface ($where)", c.tertiary, surface)
                assertReadable("${accent.key} onSecondary on secondary ($where)", c.onSecondary, c.secondary)
                assertReadable("${accent.key} onTertiary on tertiary ($where)", c.onTertiary, c.tertiary)
            }
        }
    }

    // --- the resolver -------------------------------------------------------

    @Test
    fun `an unconfigured server leaves the app blue`() {
        assertEquals(Accent.Blue, Accent.Default)
        assertEquals(Accent.Default, Accent.from(null))
        assertEquals(Accent.Default, Accent.from(""))
        assertEquals(Accent.Default, Accent.from("   "))
        assertEquals(Accent.Default, Accent.from("chartreuse"))
    }

    @Test
    fun `keys resolve regardless of casing and padding`() {
        assertEquals(Accent.Teal, Accent.from("teal"))
        assertEquals(Accent.Teal, Accent.from("  TEAL "))
        assertEquals(Accent.Purple, Accent.from("Purple"))
    }

    @Test
    fun `hex resolves to its own palette exactly`() {
        for (accent in Accent.entries) {
            val hex = "#%02X%02X%02X".format(
                (accent.base.red * 255).toInt(),
                (accent.base.green * 255).toInt(),
                (accent.base.blue * 255).toInt(),
            )
            assertEquals("$hex should resolve to ${accent.key}", accent, Accent.from(hex))
        }
    }

    @Test
    fun `an unknown hex lands on the nearest palette`() {
        // Tailwind's blue-500 and green-500: close to ours without matching.
        assertEquals(Accent.Blue, Accent.from("#3b82f6"))
        assertEquals(Accent.Green, Accent.from("#22c55e"))
        // Emerald-500 genuinely sits between our green and teal, and lands on
        // teal. Pinned so that a change to the matching is a deliberate one.
        assertEquals(Accent.Teal, Accent.from("#10b981"))
        // Shorthand, no hash, and a CSS alpha suffix all still read.
        assertEquals(Accent.Red, Accent.from("#f33"))
        assertEquals(Accent.Blue, Accent.from("3E6FF4"))
        assertEquals(Accent.Blue, Accent.from("#3E6FF4FF"))
    }

    @Test
    fun `luminance matches the WCAG reference points`() {
        assertEquals(0f, Color.Black.wcagLuminance(), 0.001f)
        assertEquals(1f, Color.White.wcagLuminance(), 0.001f)
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun `compositing a tint resolves it against what it sits on`() {
        val tint = Color.Black.copy(alpha = 0.5f)
        val flattened = tint.compositeOver(Color.White)
        assertEquals(1f, flattened.alpha, 0.001f)
        assertEquals(0.5f, flattened.red, 0.01f)
    }

    private fun theme(dark: Boolean) = if (dark) "dark" else "light"
}
