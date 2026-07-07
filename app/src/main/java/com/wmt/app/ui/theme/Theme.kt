package com.wmt.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// A fixed brand palette (Material You / dynamic color intentionally disabled) so
// the app presents the same vibrant, clean identity on every device.
private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletContainer,
    onPrimaryContainer = OnVioletContainer,
    secondary = Magenta,
    onSecondary = Color.White,
    secondaryContainer = MagentaContainer,
    onSecondaryContainer = OnMagentaContainer,
    tertiary = Color(0xFF00838F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF0F4),
    onTertiaryContainer = Color(0xFF00363B),
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = InkMuted,
    surfaceTint = Violet,
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

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF221A5E),
    primaryContainer = VioletContainerDark,
    onPrimaryContainer = VioletContainer,
    secondary = MagentaLight,
    onSecondary = Color(0xFF4A0F2B),
    secondaryContainer = Color(0xFF5C2340),
    onSecondaryContainer = MagentaContainer,
    tertiary = Color(0xFF80D8E0),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF00565E),
    onTertiaryContainer = Color(0xFFCFF0F4),
    background = InkDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceSoftDark,
    onSurfaceVariant = OnSurfaceMutedDark,
    surfaceTint = VioletLight,
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

@Composable
fun WmtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

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
