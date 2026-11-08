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
//
// The steps are deliberately weighted towards the light end. Material's filled
// Card takes surfaceContainerHighest, so whatever sits in that slot is what
// most of the app's cards look like -- put step 300 there and every card reads
// as a heavy grey-blue slab instead of paper. 300 earns its keep as the
// outline instead, where a firmer line is the point.
val Surface = Color.White
val Canvas = Slate50              // app background
val SurfaceSofter = Slate50       // scrolled app-bar / grouped background
val SurfaceSoft = Slate100        // subtle panels / chips
val SurfaceHigh = Slate200        // cards (the High and Highest slots)
val OutlineFaint = Slate100
val OutlineSoft = Slate200        // hairline dividers / borders
val Outline = Slate300
val Ink = Slate900                // primary text
val InkMuted = Slate600           // secondary text (500 is under AA on a 100 panel)

// Dark theme: the same ramp read from the other end, 900 up to 600, and the
// same reasoning -- cards land on 700, with 600 kept for the outline.
val InkDark = Slate900            // app background
val SurfaceDark = Slate900
val SurfaceSoftDark = Slate800    // panels / chips, one step off the surface
val SurfaceHighDark = Slate700    // cards
val OutlineDark = Slate700
val OutlineDarkStrong = Slate600
val OnSurfaceDark = Slate100
val OnSurfaceMutedDark = Slate300 // 400 is under AA on a 700 panel

/**
 * The ground behind a full-screen image or video. Stays near-black in both
 * themes on purpose -- a media viewer is not a surface of the app, it is the
 * absence of one, and a light backdrop would change how the content reads.
 */
val MediaBackdrop = Color(0xFF1C1C1C)

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
