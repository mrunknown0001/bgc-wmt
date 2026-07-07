package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand palette
//
// A vibrant violet-led, productivity-app look (in the spirit of modern tools
// like ClickUp) on clean neutrals. Every hue is our own — deliberately distinct
// from any third-party brand; only the general "energetic accent + saturated
// status colors on white" layout language is shared.
// ---------------------------------------------------------------------------

// Signature violet (our brand accent).
val Violet = Color(0xFF5F4BD8)
val VioletPressed = Color(0xFF4B38C4)
val VioletLight = Color(0xFFA79BF0)      // accent for dark surfaces
val VioletContainer = Color(0xFFE9E5FC)  // soft tint behind violet content (light)
val OnVioletContainer = Color(0xFF221A5E)
val VioletContainerDark = Color(0xFF3A2F8F)

// Supporting accent (secondary emphasis / highlights).
val Magenta = Color(0xFFD6437E)
val MagentaLight = Color(0xFFF29AC0)
val MagentaContainer = Color(0xFFFBDEEB)
val OnMagentaContainer = Color(0xFF4A0F2B)

// Neutrals — ink on white cards over a faint cool-gray canvas.
val Ink = Color(0xFF1E1F25)            // primary text / near-black
val InkMuted = Color(0xFF6B6F7B)       // secondary text
val Canvas = Color(0xFFFAFAFD)         // app background (faint cool gray)
val Surface = Color(0xFFFFFFFF)
val SurfaceSoft = Color(0xFFF4F4F9)    // subtle panels / chips
val SurfaceSofter = Color(0xFFF8F8FC)  // scrolled app-bar / grouped bg
val OutlineSoft = Color(0xFFE5E6EE)    // hairline dividers / borders
val OutlineFaint = Color(0xFFF0F0F6)

// Dark-theme neutrals (cool, slightly violet-tinted).
val InkDark = Color(0xFF17181E)        // app background (dark)
val SurfaceDark = Color(0xFF20222A)
val SurfaceSoftDark = Color(0xFF2A2C35)
val OnSurfaceDark = Color(0xFFE5E5EA)
val OnSurfaceMutedDark = Color(0xFF9B9DA8)
val OutlineDark = Color(0xFF3A3C47)

// ---------------------------------------------------------------------------
// Semantic status colors (shared by light & dark, tuned for tinted containers)
// ---------------------------------------------------------------------------
val StatusRed = Color(0xFFE53935)
val StatusOrange = Color(0xFFF97316)
val StatusAmber = Color(0xFFE0A100)
val StatusYellow = Color(0xFFD99E00)
val StatusBlue = Color(0xFF3E6FF4)
val StatusPurple = Color(0xFF8A4DDB)
val StatusGreen = Color(0xFF23A45C)
val StatusGray = Color(0xFF64748B)
