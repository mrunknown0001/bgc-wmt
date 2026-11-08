package com.wmt.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Identity colours: the fixed palette that tells one project or one person
// apart from another.
//
// Deliberately *not* brand. These stay put when the accent changes -- if they
// followed it, every avatar and project dot in the app would shuffle colour
// the moment somebody picked teal in web Settings, and a colour people use to
// recognise their own projects at a glance would stop meaning anything.
//
// Both lists are the eight accent mid-tones, in accent order, so the app draws
// identity from one set of hues rather than a second invented palette.
// ---------------------------------------------------------------------------

/**
 * For marks with nothing written on them -- project dots, legend swatches.
 * The mid-tones exactly as given, which is what they are good at.
 */
val ProjectDotHues: List<Color> = Accent.entries.map { it.base }

/**
 * The same eight hues, darkened until white initials read on them at AA.
 *
 * The mid-tones themselves cannot carry white text: amber manages 2.27:1 and
 * teal 2.49:1. So an avatar wears the readable shade of its hue rather than
 * the hue itself -- same identity, legible monogram.
 */
val AvatarHues: List<Color> = ProjectDotHues.map {
    it.toHsl().darkenUntilReadable(on = Color.White)
}
