package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand palette
//
// A warm-coral, high-whitespace look inspired by modern task apps. The exact
// hues are our own (distinct from any third-party brand); only the general
// "friendly warm accent on clean neutrals" layout language is shared.
// ---------------------------------------------------------------------------

// Signature warm coral (our brand accent).
val Coral = Color(0xFFF2685F)
val CoralPressed = Color(0xFFE0564D)
val CoralLight = Color(0xFFFF9089)      // accent for dark surfaces
val CoralContainer = Color(0xFFFFE3DE)  // soft tint behind coral content (light)
val OnCoralContainer = Color(0xFF4E1712)
val CoralContainerDark = Color(0xFF6E2019)

// Supporting accent (used for secondary emphasis / links).
val Indigo = Color(0xFF5A5AD6)
val IndigoLight = Color(0xFFB7B7F5)
val IndigoContainer = Color(0xFFE5E5FB)
val OnIndigoContainer = Color(0xFF16163A)

// Neutrals — the app is mostly ink-on-white with soft gray panels.
val Ink = Color(0xFF1E1F21)            // primary text / near-black
val InkMuted = Color(0xFF6E7178)       // secondary text
val Canvas = Color(0xFFFFFFFF)         // app background
val Surface = Color(0xFFFFFFFF)
val SurfaceSoft = Color(0xFFF6F6F8)    // subtle panels / chips
val SurfaceSofter = Color(0xFFFAFAFB)  // scrolled app-bar / grouped bg
val OutlineSoft = Color(0xFFE6E7EB)    // hairline dividers / borders
val OutlineFaint = Color(0xFFF0F0F3)

// Dark-theme neutrals.
val InkDark = Color(0xFF1A1B1D)        // app background (dark)
val SurfaceDark = Color(0xFF232427)
val SurfaceSoftDark = Color(0xFF2C2D31)
val OnSurfaceDark = Color(0xFFE4E4E7)
val OnSurfaceMutedDark = Color(0xFF9A9CA3)
val OutlineDark = Color(0xFF3A3B40)

// ---------------------------------------------------------------------------
// Semantic status colors (shared by light & dark, tuned for tinted containers)
// ---------------------------------------------------------------------------
val StatusRed = Color(0xFFD32F2F)
val StatusOrange = Color(0xFFEF6C00)
val StatusAmber = Color(0xFFF9A825)
val StatusYellow = Color(0xFFC8A100)
val StatusBlue = Color(0xFF1565C0)
val StatusPurple = Color(0xFF6A1B9A)
val StatusGreen = Color(0xFF2E7D32)
val StatusGray = Color(0xFF607D8B)
