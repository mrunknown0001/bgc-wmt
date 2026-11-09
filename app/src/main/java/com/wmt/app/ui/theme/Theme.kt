package com.wmt.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Material You / dynamic colour stays off: the accent is the organisation's
// choice, made in WMT web and followed here, not the device wallpaper's.
// Everything brand-coloured below is derived from that one accent, so the
// neutrals are the only fixed colours left in the schemes.

private fun lightSchemeFor(accent: Accent): ColorScheme {
    val a = accent.accentColors(dark = false, surface = Surface)
    // Errors stay red whatever the accent is. The pair is derived the same way
    // as everything else, so "red" cannot quietly mean "unreadable red".
    val error = StatusRed.toHsl().darkenUntilReadable(on = Surface)
    val errorBox = badgeColorsOn(StatusRed, Surface)
    return lightColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.primaryContainer,
        onPrimaryContainer = a.onPrimaryContainer,
        secondary = a.secondary,
        onSecondary = a.onSecondary,
        secondaryContainer = a.secondaryContainer,
        onSecondaryContainer = a.onSecondaryContainer,
        tertiary = a.tertiary,
        onTertiary = a.onTertiary,
        tertiaryContainer = a.tertiaryContainer,
        onTertiaryContainer = a.onTertiaryContainer,
        background = Canvas,
        onBackground = Ink,
        surface = Surface,
        onSurface = Ink,
        surfaceVariant = SurfaceSoft,
        onSurfaceVariant = InkMuted,
        surfaceTint = a.primary,
        surfaceContainerLowest = Surface,
        surfaceContainerLow = SurfaceSofter,
        surfaceContainer = SurfaceSoft,
        // The ramp gives four usable steps on this side, so the top two
        // elevations share one. Better a repeated step than a hex invented
        // between them -- and this is the step cards are drawn on.
        surfaceContainerHigh = SurfaceHigh,
        surfaceContainerHighest = SurfaceHigh,
        outline = Outline,
        outlineVariant = OutlineSoft,
        error = error,
        onError = Color.White,
        errorContainer = errorBox.container,
        onErrorContainer = errorBox.content,
        scrim = Color.Black,
    )
}

private fun darkSchemeFor(accent: Accent): ColorScheme {
    val a = accent.accentColors(dark = true, surface = SurfaceDark)
    val errorHsl = StatusRed.toHsl()
    val error = errorHsl.lightenUntilReadable(on = SurfaceDark)
    val errorBox = badgeColorsOn(StatusRed, SurfaceDark)
    return darkColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.primaryContainer,
        onPrimaryContainer = a.onPrimaryContainer,
        secondary = a.secondary,
        onSecondary = a.onSecondary,
        secondaryContainer = a.secondaryContainer,
        onSecondaryContainer = a.onSecondaryContainer,
        tertiary = a.tertiary,
        onTertiary = a.onTertiary,
        tertiaryContainer = a.tertiaryContainer,
        onTertiaryContainer = a.onTertiaryContainer,
        background = InkDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceSoftDark,
        onSurfaceVariant = OnSurfaceMutedDark,
        surfaceTint = a.primary,
        surfaceContainerLowest = InkDark,
        surfaceContainerLow = SurfaceSoftDark,
        surfaceContainer = SurfaceSoftDark,
        surfaceContainerHigh = SurfaceHighDark,
        surfaceContainerHighest = SurfaceHighDark,
        outline = OutlineDarkStrong,
        outlineVariant = OutlineDark,
        error = error,
        onError = errorHsl.darkenUntilReadable(on = error),
        errorContainer = errorBox.container,
        onErrorContainer = errorBox.content,
        scrim = Color.Black,
    )
}

/** Built once per accent per theme; the derivation walks lightness and need not rerun. */
internal fun schemeFor(accent: Accent, dark: Boolean): ColorScheme =
    if (dark) darkSchemeFor(accent) else lightSchemeFor(accent)

@Composable
fun WmtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(accent, darkTheme) { schemeFor(accent, darkTheme) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = WmtShapes,
    ) {
        // The window background underneath can only follow the *system* night setting
        // (see res/values/themes.xml), so on a phone set to light with the app set to
        // dark -- or the other way round -- it is the opposite brightness to the text.
        // Screens that paint their own background never noticed; Scaffold does it for
        // every signed-in screen. The splash, server setup and login screens do not,
        // and their text was left all but invisible against it. Painting the theme's
        // own background here means no screen depends on what the window happens to be.
        Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
            content()
        }
    }
}
