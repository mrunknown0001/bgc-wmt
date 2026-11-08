package com.wmt.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Material You / dynamic colour stays off: the accent is the organisation's
// choice, made in WMT web and followed here, not the device wallpaper's.
// Everything brand-coloured below is derived from that one accent, so the
// neutrals are the only fixed colours left in the schemes.

private fun lightSchemeFor(accent: Accent): ColorScheme {
    val a = accent.accentColors(dark = false, surface = Surface)
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
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = SurfaceSofter,
        surfaceContainer = SurfaceSoft,
        surfaceContainerHigh = Color(0xFFEFEFF2),
        surfaceContainerHighest = Color(0xFFE9E9ED),
        outline = Color(0xFFC9CBD1),
        outlineVariant = OutlineSoft,
        error = StatusRed,
        onError = Color.White,
        errorContainer = Color(0xFFFCE4E4),
        onErrorContainer = Color(0xFF5A1414),
        scrim = Color.Black,
    )
}

private fun darkSchemeFor(accent: Accent): ColorScheme {
    val a = accent.accentColors(dark = true, surface = SurfaceDark)
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
        surfaceContainerLowest = Color(0xFF141517),
        surfaceContainerLow = Color(0xFF202124),
        surfaceContainer = SurfaceSoftDark,
        surfaceContainerHigh = Color(0xFF333438),
        surfaceContainerHighest = Color(0xFF3D3E43),
        outline = Color(0xFF55565C),
        outlineVariant = OutlineDark,
        error = Color(0xFFFF897D),
        onError = Color(0xFF5A1414),
        errorContainer = Color(0xFF5C1A1A),
        onErrorContainer = Color(0xFFFFDAD5),
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
        content = content,
    )
}
