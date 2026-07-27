package com.example.ui.helper

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.SpaceCyan
import com.example.ui.theme.SpaceTextPrimary
import com.example.ui.theme.SpaceViolet
import kotlin.random.Random

private data class Star(val xFrac: Float, val yFrac: Float, val radius: Float, val phaseOffset: Float, val speed: Int)

/**
 * Animated deep-space backdrop: a fixed star field that twinkles, plus a moon and one or two
 * ringed planets that drift slowly and rotate. Pure decoration - drawn once behind screen
 * content, does not intercept touch or affect layout of what's placed on top of it.
 */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val rng = Random(42)
        List(70) {
            Star(
                xFrac = rng.nextFloat(),
                yFrac = rng.nextFloat(),
                radius = rng.nextFloat() * 1.6f + 0.6f,
                phaseOffset = rng.nextFloat() * 6.28f,
                speed = rng.nextInt(2200, 4200)
            )
        }
    }

    val infinite = rememberInfiniteTransition(label = "space_bg")
    val twinkle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )
    val drift by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val ringSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring_spin"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Star field - each star twinkles at its own phase/speed.
            stars.forEach { star ->
                val t = twinkle * (2200f / star.speed) + star.phaseOffset
                val alpha = (0.15f + 0.85f * ((kotlin.math.sin(t) + 1f) / 2f)).coerceIn(0.1f, 1f)
                drawCircle(
                    color = SpaceTextPrimary.copy(alpha = alpha),
                    radius = star.radius,
                    center = Offset(star.xFrac * w, star.yFrac * h)
                )
            }

            // Moon - upper right, faint drift.
            val moonCenter = Offset(w * 0.82f + drift * 6f, h * 0.09f)
            val moonRadius = w * 0.09f
            drawCircle(color = Color(0xFFD9DEE8), radius = moonRadius, center = moonCenter)
            drawCircle(color = Color(0xFFB9C0CE), radius = moonRadius * 0.14f, center = moonCenter + Offset(-moonRadius * 0.3f, -moonRadius * 0.25f))
            drawCircle(color = Color(0xFFB9C0CE), radius = moonRadius * 0.1f, center = moonCenter + Offset(moonRadius * 0.25f, moonRadius * 0.15f))

            // Ringed planet - lower left, slow drift + spinning ring.
            val planetCenter = Offset(w * 0.16f - drift * 10f, h * 0.62f)
            val planetRadius = w * 0.075f
            rotate(degrees = ringSpin, pivot = planetCenter) {
                drawOval(
                    color = SpaceViolet.copy(alpha = 0.55f),
                    topLeft = Offset(planetCenter.x - planetRadius * 1.8f, planetCenter.y - planetRadius * 0.5f),
                    size = androidx.compose.ui.geometry.Size(planetRadius * 3.6f, planetRadius),
                    style = Stroke(width = 2f)
                )
            }
            drawCircle(color = Color(0xFF161B33), radius = planetRadius, center = planetCenter)
            drawCircle(color = SpaceCyan.copy(alpha = 0.08f), radius = planetRadius, center = planetCenter + Offset(-planetRadius * 0.25f, -planetRadius * 0.25f))

            // Small distant planet - lower right.
            val smallCenter = Offset(w * 0.9f + drift * 5f, h * 0.85f)
            val smallRadius = w * 0.045f
            drawCircle(color = Color(0xFF1C2244), radius = smallRadius, center = smallCenter)
            drawCircle(color = SpaceCyan.copy(alpha = 0.06f), radius = smallRadius, center = smallCenter)
        }
    }
}
