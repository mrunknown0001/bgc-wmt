package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Neutrals and status colours.
//
// There is no brand colour in here any more: the accent is whatever the
// organisation chose in WMT web, and every brand-coloured slot is derived from
// it in Accent.kt. What is left is the ground it all sits on -- neutrals -- and
// the semantic status hues, which mean the same thing whatever the accent is.
// ---------------------------------------------------------------------------

// The neutral ramp, shared with the web app. Cool-tinted rather than pure
// grey, which is also where StatusGray below comes from -- it is step 500.
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)

// Light theme: white cards stepping up through the ramp, ink on top.
val Surface = Color.White
val Canvas = Slate50              // app background
val SurfaceSofter = Slate50       // scrolled app-bar / grouped background
val SurfaceSoft = Slate100        // subtle panels / chips
val SurfaceHigh = Slate200
val SurfaceHighest = Slate300
val OutlineFaint = Slate100
val OutlineSoft = Slate200        // hairline dividers / borders
val Outline = Slate300
val Ink = Slate900                // primary text
val InkMuted = Slate600           // secondary text (500 is under AA on a 100 panel)

// Dark theme: the same ramp read from the other end, 900 up to 600.
val InkDark = Slate900            // app background
val SurfaceDark = Slate800
val SurfaceSoftDark = Slate700
val SurfaceHighDark = Slate600
val OutlineDark = Slate700
val OutlineDarkStrong = Slate600
val OnSurfaceDark = Slate100
val OnSurfaceMutedDark = Slate300 // 400 is under AA on a 700 panel

// ---------------------------------------------------------------------------
// Semantic status colours (shared by light & dark; each badge derives a
// readable label against its own tint -- see StatusColors.kt)
// ---------------------------------------------------------------------------
val StatusRed = Color(0xFFE53935)
val StatusOrange = Color(0xFFF97316)
val StatusAmber = Color(0xFFE0A100)
val StatusYellow = Color(0xFFD99E00)
val StatusBlue = Color(0xFF3E6FF4)
val StatusPurple = Color(0xFF8A4DDB)
val StatusGreen = Color(0xFF23A45C)
val StatusGray = Slate500
