package com.vythera.stride.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val isCircle: Boolean,
    val originX: Float,
    val drift: Float
)

/**
 * Full-screen celebration burst. Re-fires whenever [burstKey] increments past 0.
 */
@Composable
fun ConfettiBurst(
    burstKey: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    if (burstKey <= 0) return
    val progress = remember(burstKey) { Animatable(0f) }
    var particles by remember(burstKey) { mutableStateOf<List<Particle>>(emptyList()) }

    LaunchedEffect(burstKey) {
        particles = List(140) {
            Particle(
                angle = (-95f + Random.nextFloat() * 100f - 50f),
                speed = 0.55f + Random.nextFloat() * 1.15f,
                size = 8f + Random.nextFloat() * 16f,
                color = colors[Random.nextInt(colors.size)],
                spin = Random.nextFloat() * 720f - 360f,
                isCircle = Random.nextBoolean(),
                originX = 0.15f + Random.nextFloat() * 0.7f,
                drift = Random.nextFloat() * 0.3f - 0.15f
            )
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 2400, easing = LinearEasing))
    }

    val t = progress.value
    if (t >= 1f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val rad = Math.toRadians(p.angle.toDouble())
            val dist = p.speed * h * 0.55f * t
            val gravity = 1300f * t * t
            val x = p.originX * w + (cos(rad) * dist).toFloat() + p.drift * w * t
            val y = h * 0.42f + (sin(rad) * dist).toFloat() + gravity * 0.35f
            val alpha = (1f - t).coerceIn(0f, 1f)
            rotate(degrees = p.spin * t, pivot = Offset(x, y)) {
                if (p.isCircle) {
                    drawCircle(p.color.copy(alpha = alpha), radius = p.size / 2, center = Offset(x, y))
                } else {
                    drawRect(
                        p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size / 2, y - p.size / 4),
                        size = Size(p.size, p.size / 2)
                    )
                }
            }
        }
    }
}
