package com.wmt.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

private val confettiColors = listOf(
    Color(0xFF5F4BD8), // brand violet
    Color(0xFFD6437E), // magenta
    Color(0xFF3E6FF4), // blue
    Color(0xFF23A45C), // green
    Color(0xFFF97316), // orange
    Color(0xFFE0A100), // amber
    Color(0xFF00A3B4), // teal
)

private class Particle(
    val x0: Float,      // spawn x (fraction of width)
    val y0: Float,      // spawn y (fraction of height)
    val vx: Float,      // horizontal velocity (fraction of width per animation)
    val vy: Float,      // initial vertical velocity (negative = upward)
    val width: Float,   // px
    val height: Float,  // px
    val spin: Float,    // total rotations over the animation
    val color: Color,
) {
    companion object {
        fun burst() = Particle(
            x0 = 0.5f + Random.nextFloat() * 0.2f - 0.1f,
            y0 = 0.42f + Random.nextFloat() * 0.06f,
            vx = Random.nextFloat() * 0.9f - 0.45f,
            vy = -(0.25f + Random.nextFloat() * 0.55f),
            width = 12f + Random.nextFloat() * 14f,
            height = 7f + Random.nextFloat() * 9f,
            spin = (Random.nextFloat() * 4f - 2f),
            color = confettiColors[Random.nextInt(confettiColors.size)],
        )
    }
}

/**
 * A short center-burst confetti overlay (the "task completed" celebration).
 * Fires each time [burstKey] increments past 0; draws nothing when idle and
 * never intercepts touches.
 */
@Composable
fun ConfettiEffect(burstKey: Int, modifier: Modifier = Modifier) {
    if (burstKey <= 0) return
    val particles = remember(burstKey) { List(90) { Particle.burst() } }
    val progress = remember(burstKey) { Animatable(0f) }

    LaunchedEffect(burstKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1700, easing = LinearEasing))
    }

    val t = progress.value
    if (t >= 1f) return

    Canvas(modifier.fillMaxSize()) {
        val gravity = 1.1f
        val fade = (1f - t * t).coerceIn(0f, 1f)
        particles.forEach { p ->
            val x = (p.x0 + p.vx * t) * size.width
            val y = (p.y0 + p.vy * t + gravity * t * t) * size.height
            if (y > size.height || x < -40f || x > size.width + 40f) return@forEach
            rotate(degrees = p.spin * t * 360f, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                    size = Size(p.width, p.height),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
    }
}
