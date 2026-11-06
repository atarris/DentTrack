package com.stocktrack.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

private val VeinGreen get() = DentTrackColors.Primary     // used at very low opacity

/**
 * Background for the start screen: near-white with soft mint waves in the upper-left and lower-right
 * corners that move gently, faint marble veins that drift slowly, and a shine that sweeps over the
 * screen every few seconds (a quiet live wallpaper).
 */
@Composable
fun MarbleBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // The Blue scheme has a plain background, like the web page.
    if (DentTrackColors.palette.flat) {
        Box(modifier.background(DentTrackColors.AppBackground), contentAlignment = Alignment.Center, content = content)
        return
    }

    val transition = rememberInfiniteTransition(label = "marble")

    // 0..1 sweep of the shine, once per cycle, then a pause before the next one.
    val shine = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 14_000
                0f at 3_000
                1f at 8_500 using LinearOutSlowInEasing
                1f at 14_000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shine"
    )
    // Slow breathing of the gradient and the veins (about 40 s per loop).
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Read in the draw phase only: the animation redraws the canvas, it does not recompose.
            drawMarble(shine.value, drift.value)
        }
        content()
    }
}

private fun DrawScope.drawMarble(shine: Float, drift: Float) {
    val w = size.width
    val h = size.height
    val breathe = sin(drift)
    val sway = sin(drift + 1.6f)

    // 1) Base: near-white, with two layers of soft mint waves in opposite corners.
    drawRect(DentTrackColors.AppBackground)
    val mint = DentTrackColors.Mint
    val topLeft = Offset(0f, 0f)
    val bottomRight = Offset(w, h)
    drawPath(
        Path().apply {
            moveTo(0f, 0f)
            lineTo(w * (0.38f + 0.015f * breathe), 0f)
            cubicTo(
                w * 0.27f, h * (0.10f + 0.015f * sway),
                w * 0.13f, h * (0.12f - 0.015f * breathe),
                0f, h * (0.24f + 0.012f * sway)
            )
            close()
        },
        Brush.linearGradient(listOf(mint, mint.copy(alpha = 0.35f)), topLeft, Offset(w * 0.38f, h * 0.24f))
    )
    drawPath(
        Path().apply {
            moveTo(0f, 0f)
            lineTo(w * (0.24f - 0.012f * sway), 0f)
            cubicTo(
                w * 0.17f, h * (0.06f - 0.012f * breathe),
                w * 0.08f, h * (0.07f + 0.012f * sway),
                0f, h * (0.15f - 0.010f * breathe)
            )
            close()
        },
        mint.copy(alpha = 0.55f)
    )
    drawPath(
        Path().apply {
            moveTo(w, h)
            lineTo(w * (0.58f - 0.015f * breathe), h)
            cubicTo(
                w * 0.72f, h * (0.92f - 0.015f * sway),
                w * 0.85f, h * (0.76f + 0.015f * breathe),
                w, h * (0.66f - 0.012f * sway)
            )
            close()
        },
        Brush.linearGradient(listOf(mint, mint.copy(alpha = 0.35f)), bottomRight, Offset(w * 0.60f, h * 0.66f))
    )
    drawPath(
        Path().apply {
            moveTo(w, h)
            lineTo(w * (0.74f + 0.012f * sway), h)
            cubicTo(
                w * 0.83f, h * (0.95f + 0.012f * breathe),
                w * 0.92f, h * (0.86f - 0.012f * sway),
                w, h * (0.80f + 0.010f * breathe)
            )
            close()
        },
        mint.copy(alpha = 0.55f)
    )

    // 2) Marble veins: a few long, thin curves, strongest near the green corner.
    val veins = listOf(
        Triple(0.18f, 0.0f, 0.10f), Triple(0.34f, 1.3f, 0.07f), Triple(0.52f, 2.4f, 0.05f),
        Triple(0.70f, 3.6f, 0.04f), Triple(0.88f, 4.9f, 0.03f)
    )
    veins.forEachIndexed { i, (yFraction, phase, alpha) ->
        val wobble = sin(drift + phase) * h * 0.025f
        val y0 = h * yFraction
        val path = Path().apply {
            moveTo(w * 1.05f, y0 - h * 0.18f + wobble)
            cubicTo(
                w * 0.78f, y0 + h * 0.10f - wobble,
                w * 0.52f, y0 - h * 0.12f + wobble,
                w * 0.28f - i * w * 0.03f, y0 + h * 0.22f - wobble
            )
            cubicTo(
                w * 0.16f, y0 + h * 0.38f + wobble,
                w * 0.05f, y0 + h * 0.30f,
                -w * 0.05f, y0 + h * 0.46f - wobble
            )
        }
        drawPath(path, VeinGreen.copy(alpha = alpha), style = Stroke(width = (1.2f + (4 - i) * 0.5f) * density))
        // A thinner, lighter companion line next to each vein, like the fine cracks in real marble.
        drawPath(
            path,
            VeinGreen.copy(alpha = alpha * 0.6f),
            style = Stroke(width = 0.8f * density)
        )
    }

    // 3) Shine: a soft slanted band (green-tinted halo + bright core) travelling from the upper-right
    //    corner to the lower-left one. While shine == 0 or 1 it rests outside the screen: nothing drawn.
    if (shine > 0f && shine < 1f) {
        val origin = Offset(w, 0f)                       // upper-right corner
        val toEnd = Offset(-w, h)                        // to the lower-left corner
        val length = sqrt(toEnd.x * toEnd.x + toEnd.y * toEnd.y)
        val ux = toEnd.x / length
        val uy = toEnd.y / length
        val centre = length * (-0.25f + 1.5f * shine)    // starts before the corner, ends beyond the far one
        fun band(half: Float, colors: List<Color>) = Brush.linearGradient(
            colors = colors,
            start = Offset(origin.x + ux * (centre - half), origin.y + uy * (centre - half)),
            end = Offset(origin.x + ux * (centre + half), origin.y + uy * (centre + half))
        )
        drawRect(band(length * 0.30f, listOf(Color.Transparent, VeinGreen.copy(alpha = 0.07f), Color.Transparent)))
        drawRect(band(length * 0.13f, listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent)))
    }
}
