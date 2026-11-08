package com.wmt.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assembled schemes, not just the accent maths.
 *
 * [AccentContrastTest] proves the derivation; this proves what the derivation
 * was actually wired into -- neutrals, error slots and all. It is the version
 * that would catch a ramp step put in the wrong slot, which the accent tests
 * on their own would sail straight past.
 */
class SchemeContrastTest {

    private val aa = 4.5f

    private fun pairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onSurface on surface", s.onSurface, s.surface),
        Triple("onSurfaceVariant on surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
        Triple("onSurfaceVariant on surface", s.onSurfaceVariant, s.surface),
        Triple("onBackground on background", s.onBackground, s.background),
        Triple("onSurface on surfaceContainer", s.onSurface, s.surfaceContainer),
        Triple("onSurface on surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest),
        Triple("onPrimary on primary", s.onPrimary, s.primary),
        Triple("primary on surface", s.primary, s.surface),
        Triple("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondary on secondary", s.onSecondary, s.secondary),
        Triple("onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiary on tertiary", s.onTertiary, s.tertiary),
        Triple("onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onError on error", s.onError, s.error),
        Triple("error on surface", s.error, s.surface),
        Triple("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer),
    )

    @Test
    fun `every scheme pair clears AA for all eight accents in both themes`() {
        val failures = mutableListOf<String>()
        for (accent in Accent.entries) {
            for (dark in listOf(false, true)) {
                val scheme = schemeFor(accent, dark)
                val theme = if (dark) "dark" else "light"
                for ((label, foreground, background) in pairs(scheme)) {
                    val ratio = contrastRatio(foreground, background)
                    if (ratio < aa) {
                        failures += "%s/%s: %s is %.2f:1".format(accent.key, theme, label, ratio)
                    }
                }
            }
        }
        assertTrue(
            "pairs below the $aa:1 AA bar:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /**
     * No magenta anywhere -- including magenta nobody typed.
     *
     * Deriving tertiary by rotating the accent hue (Material's usual +60
     * degrees) turned blue, indigo and purple into magenta, and blue is the
     * default, so a fresh install showed it on the dashboard tiles. Hue is
     * checked rather than a list of hexes because the offending colour was
     * never written down anywhere to be grepped for.
     */
    @Test
    fun `no scheme colour drifts into magenta`() {
        val magenta = 285f..345f
        val offenders = mutableListOf<String>()
        for (accent in Accent.entries) {
            for (dark in listOf(false, true)) {
                val s = schemeFor(accent, dark)
                val named = listOf(
                    "primary" to s.primary, "secondary" to s.secondary, "tertiary" to s.tertiary,
                    "primaryContainer" to s.primaryContainer,
                    "secondaryContainer" to s.secondaryContainer,
                    "tertiaryContainer" to s.tertiaryContainer,
                )
                for ((name, color) in named) {
                    val hsl = color.toHsl()
                    // Saturation guard: a near-grey has a hue, but no colour.
                    if (hsl.s > 0.15f && hsl.h in magenta) {
                        offenders += "%s/%s %s is hue %.0f".format(
                            accent.key, if (dark) "dark" else "light", name, hsl.h,
                        )
                    }
                }
            }
        }
        assertTrue("magenta found:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `the neutral ramp is ordered from lightest to darkest`() {
        val ramp = listOf(
            "50" to Slate50, "100" to Slate100, "200" to Slate200, "300" to Slate300,
            "400" to Slate400, "500" to Slate500, "600" to Slate600, "700" to Slate700,
            "800" to Slate800, "900" to Slate900,
        )
        for ((lighter, darker) in ramp.zipWithNext()) {
            assertTrue(
                "slate-${lighter.first} should be lighter than slate-${darker.first}",
                lighter.second.wcagLuminance() > darker.second.wcagLuminance(),
            )
        }
    }
}
