package com.stocktrack.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.stocktrack.app.data.Season
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The animated backgrounds of the hidden themes, all drawn in code so they need no image files.
 * The middle of the screen is covered by the user cards, so the big pictures sit in the corners and
 * along the edges. Each picture is drawn around (0, 0) in its own units and placed with [at].
 */
@Composable
fun SeasonBackground(season: Season, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "season")
    // Seconds since the screen opened, starting again after an hour.
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(tween(3_600_000, easing = LinearEasing), RepeatMode.Restart),
        label = "time"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Read in the draw phase only: the animation redraws the canvas, it does not recompose.
            val t = time.value
            when (season) {
                Season.HALLOWEEN -> drawHalloween(t)
                Season.CHRISTMAS -> drawChristmas(t)
                Season.NEW_YEAR -> drawNewYear(t)
                Season.VALENTINE -> drawValentine(t)
                Season.INDEPENDENCE -> drawIndependence(t)
                Season.MARCH_11 -> drawMarch11(t)
                Season.JONINES -> drawJonines(t)
                Season.MINDAUGINES -> drawMindaugines(t)
                Season.BIRTHDAY -> drawBirthday(t)
            }
        }
        content()
    }
}

/** The colour of the greeting banner. */
fun seasonAccent(season: Season): Color = when (season) {
    Season.HALLOWEEN -> Color(0xFFD84A05)
    Season.CHRISTMAS -> Color(0xFFB71C1C)
    Season.NEW_YEAR -> Color(0xFF283593)
    Season.VALENTINE -> Color(0xFFC2185B)
    Season.INDEPENDENCE -> Tricolor.Green
    Season.MARCH_11 -> Tricolor.Red
    Season.JONINES -> Color(0xFF2E7D32)
    Season.MINDAUGINES -> Color(0xFF8A6500)
    Season.BIRTHDAY -> Color(0xFF8E24AA)
}

// ------------------------------------------------------------------ shared

private object Tricolor {
    val Yellow = Color(0xFFFDB913)
    val Green = Color(0xFF006A44)
    val Red = Color(0xFFC1272D)
}

private val Gold = Color(0xFFFFC83D)
private val FestiveColors = listOf(
    Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFFAB40)
)

/** Scattered things that stay the same from frame to frame. All fields are 0..1. */
private class Bit(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float)

private fun bits(count: Int, seed: Int): List<Bit> {
    val r = Random(seed)
    return List(count) { Bit(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) }
}

private val Stars = bits(70, 11)
private val Snow = bits(90, 12)
private val Confetti = bits(60, 13)
private val Hearts = bits(30, 14)
private val Balloons = bits(14, 15)
private val Fireflies = bits(26, 16)
private val Sparks = bits(24, 17)
private val Bats = bits(8, 18)

private fun fraction(value: Float): Float = value - floor(value)

private val DrawScope.w get() = size.width
private val DrawScope.h get() = size.height

/** One unit of the pictures, so they keep their proportions on any screen. */
private val DrawScope.unit get() = min(size.width, size.height) / 100f

private inline fun DrawScope.at(x: Float, y: Float, scale: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(x, y)
        scale(scale, scale, Offset.Zero)
    }, block)
}

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) {
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
}

private fun DrawScope.sky(colors: List<Color>) {
    drawRect(Brush.verticalGradient(colors))
}

private fun DrawScope.stars(t: Float, color: Color = Color.White, maxY: Float = 0.65f) {
    Stars.forEach { s ->
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + s.speed * 2f) + s.phase * 6.28f))
        drawCircle(color.copy(alpha = twinkle), (0.5f + s.size) * unit * 0.28f, Offset(s.x * w, s.y * h * maxY))
    }
}

private fun DrawScope.confetti(t: Float, colors: List<Color>) {
    Confetti.forEachIndexed { i, c ->
        val y = fraction(c.y + t * (0.04f + c.speed * 0.05f)) * (h * 1.1f) - h * 0.05f
        val x = c.x * w + sin(t * 1.2f + c.phase * 6.28f) * unit * 2.5f
        val side = (0.5f + c.size * 0.6f) * unit
        rotate(t * 90f * (0.5f + c.speed) + c.phase * 360f, Offset(x, y)) {
            drawRect(colors[i % colors.size], Offset(x - side / 2, y - side / 4), Size(side, side / 2))
        }
    }
}

private fun hash(n: Int): Float = fraction(sin(n * 12.9898f) * 43758.547f)

/** Rockets from the bottom that burst in the sky, one after the other. Bursts stay near the two sides. */
private fun DrawScope.fireworks(t: Float, count: Int) {
    repeat(count) { i ->
        val period = 3.6f + i * 0.35f
        val position = t / period + i / count.toFloat()
        val round = floor(position).toInt()
        val local = position - round
        val left = (round + i) % 2 == 0
        val x = (if (left) 0.03f + hash(round * 7 + i) * 0.17f else 0.8f + hash(round * 11 + i) * 0.17f) * w
        val y = (0.08f + hash(round * 13 + i) * 0.34f) * h
        val color = FestiveColors[(round + i) % FestiveColors.size]
        if (local < 0.22f) {
            val rise = local / 0.22f
            val rocketY = h + (y - h) * rise
            drawLine(
                color.copy(alpha = 0.8f), Offset(x, rocketY), Offset(x, rocketY + unit * 3.5f),
                strokeWidth = unit * 0.35f, cap = StrokeCap.Round
            )
        } else {
            burst(x, y, (local - 0.22f) / 0.78f, color, unit * (10f + hash(round + i) * 8f))
        }
    }
}

private fun DrawScope.burst(cx: Float, cy: Float, age: Float, color: Color, radius: Float) {
    val alpha = (1f - age) * (1f - age)
    val spread = 1f - (1f - age) * (1f - age) * (1f - age)
    repeat(30) { i ->
        val angle = i / 30f * 2f * PI.toFloat()
        val length = radius * spread * (if (i % 2 == 0) 1f else 0.72f)
        val fall = age * age * unit * 5f
        val center = Offset(cx + cos(angle) * length, cy + sin(angle) * length + fall)
        drawCircle(color.copy(alpha = alpha), unit * 0.42f, center)
        drawCircle(Color.White.copy(alpha = alpha * 0.8f), unit * 0.16f, center)
    }
}

// --------------------------------------------------------------- Halloween

private fun DrawScope.drawHalloween(t: Float) {
    sky(listOf(Color(0xFF120A26), Color(0xFF2B1248), Color(0xFF6B2A10)))
    stars(t, maxY = 0.5f)

    // The moon, with a glow that breathes.
    val moon = Offset(w * 0.88f, h * 0.17f)
    val glow = 1f + 0.05f * sin(t * 0.8f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0x66FFE9A8), Color.Transparent), moon, unit * 24f * glow),
        unit * 24f * glow, moon
    )
    drawCircle(Color(0xFFFFEDB3), unit * 11f, moon)
    drawCircle(Color(0x33B8924A), unit * 2.4f, moon + Offset(-unit * 3f, -unit * 2.5f))
    drawCircle(Color(0x33B8924A), unit * 1.6f, moon + Offset(unit * 3.5f, unit * 3f))
    drawCircle(Color(0x33B8924A), unit * 1.1f, moon + Offset(-unit * 2f, unit * 5f))

    // Hills, then a bare tree on each side.
    drawPath(
        Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.84f)
            cubicTo(w * 0.2f, h * 0.76f, w * 0.34f, h * 0.92f, w * 0.52f, h * 0.87f)
            cubicTo(w * 0.7f, h * 0.81f, w * 0.84f, h * 0.9f, w, h * 0.8f)
            lineTo(w, h)
            close()
        },
        Color(0xFF0D0716)
    )
    at(w * 0.06f, h * 0.86f, unit, DrawScope::spookyTree)
    at(w * 0.95f, h * 0.84f, unit * 0.8f, DrawScope::spookyTree)

    // Spider on its thread in the corner.
    val swing = sin(t * 0.9f) * unit * 3f
    val spider = Offset(w * 0.17f + swing, h * 0.13f + (0.5f + 0.5f * sin(t * 0.5f)) * h * 0.06f)
    drawLine(Color(0x99FFFFFF), Offset(w * 0.17f, 0f), spider, strokeWidth = unit * 0.1f)
    at(spider.x, spider.y, unit, DrawScope::spider)

    // Ghosts drift along the sides.
    at(w * 0.08f + sin(t * 0.4f) * unit * 4f, h * 0.42f + sin(t * 0.9f) * unit * 3f, unit, ghost(t))
    at(w * 0.93f + sin(t * 0.33f + 2f) * unit * 4f, h * 0.5f + sin(t * 0.7f + 1f) * unit * 3f, unit * 0.8f, ghost(t + 2f))

    // Pumpkins along the bottom, candles flickering.
    listOf(
        Triple(0.13f, 0.9f, 1.25f), Triple(0.27f, 0.93f, 0.85f), Triple(0.42f, 0.95f, 0.7f),
        Triple(0.6f, 0.95f, 0.8f), Triple(0.76f, 0.93f, 1f), Triple(0.88f, 0.9f, 1.4f)
    ).forEachIndexed { i, (x, y, scale) ->
        val flicker = 0.72f + 0.28f * sin(t * (7f + i) + i * 2f) * sin(t * (3.1f + i * 0.4f))
        at(w * x, h * y, unit * scale) { pumpkin(flicker) }
    }

    // Bats cross the screen in big loops.
    Bats.forEach { b ->
        val pass = fraction(t * (0.025f + b.speed * 0.025f) + b.phase)
        val x = -0.1f + pass * 1.2f
        val y = 0.08f + b.y * 0.35f + sin(pass * 18f + b.phase * 6f) * 0.05f
        val flap = sin(t * (9f + b.speed * 5f) + b.phase * 6f)
        at(w * x, h * y, unit * (0.65f + b.size * 0.7f)) { bat(flap) }
    }
}

private fun DrawScope.spookyTree() {
    val bark = Color(0xFF0B0612)
    fun branch(x0: Float, y0: Float, x1: Float, y1: Float, width: Float) =
        drawLine(bark, Offset(x0, y0), Offset(x1, y1), strokeWidth = width, cap = StrokeCap.Round)
    branch(0f, 0f, 0f, -22f, 3.4f)
    branch(0f, -14f, -9f, -24f, 1.8f)
    branch(0f, -19f, 8f, -31f, 1.8f)
    branch(0f, -22f, -3f, -36f, 1.5f)
    branch(-9f, -24f, -14f, -25f, 0.8f)
    branch(-9f, -24f, -10f, -31f, 0.8f)
    branch(8f, -31f, 14f, -33f, 0.8f)
    branch(8f, -31f, 7f, -38f, 0.8f)
    branch(-3f, -36f, -7f, -41f, 0.7f)
    branch(-3f, -36f, 1f, -42f, 0.7f)
}

private fun DrawScope.spider() {
    val body = Color(0xFF0B0612)
    listOf(-1f, 1f).forEach { side ->
        listOf(-0.9f, -0.2f, 0.5f, 1.1f).forEach { leg ->
            drawLine(body, Offset(0f, leg * 0.5f), Offset(side * 2.4f, leg * 1.4f), strokeWidth = 0.22f)
        }
    }
    oval(body, 0f, 0f, 1.1f, 1.4f)
    drawCircle(Color(0xFFFF5252), 0.2f, Offset(-0.4f, -0.6f))
    drawCircle(Color(0xFFFF5252), 0.2f, Offset(0.4f, -0.6f))
}

private fun ghost(t: Float): DrawScope.() -> Unit = {
    val white = Color(0xE6F4F1FF)
    drawPath(
        Path().apply {
            moveTo(-4f, 5f)
            cubicTo(-4.4f, -8f, 4.4f, -8f, 4f, 5f)
            val wave = sin(t * 3f)
            quadraticTo(3f, 7f + wave, 2f, 5f)
            quadraticTo(1f, 7f - wave, 0f, 5f)
            quadraticTo(-1f, 7f + wave, -2f, 5f)
            quadraticTo(-3f, 7f - wave, -4f, 5f)
            close()
        },
        white
    )
    oval(Color(0xFF1A1030), -1.4f, -2f, 0.7f, 1f)
    oval(Color(0xFF1A1030), 1.4f, -2f, 0.7f, 1f)
    oval(Color(0xFF1A1030), 0f, 0.6f, 0.8f, 1.1f)
}

private fun DrawScope.bat(flap: Float) {
    val lift = flap * 2.6f
    drawPath(
        Path().apply {
            moveTo(0f, -0.6f)
            quadraticTo(-2f, -1.6f - lift, -5f, -lift)
            quadraticTo(-3.4f, 0.1f, -2.8f, 1f - lift * 0.3f)
            quadraticTo(-1.6f, 0.4f, -0.6f, 1.2f)
            lineTo(0.6f, 1.2f)
            quadraticTo(1.6f, 0.4f, 2.8f, 1f - lift * 0.3f)
            quadraticTo(3.4f, 0.1f, 5f, -lift)
            quadraticTo(2f, -1.6f - lift, 0f, -0.6f)
            close()
        },
        Color(0xFF07040D)
    )
    oval(Color(0xFF07040D), 0f, 0f, 0.8f, 1.4f)
    drawLine(Color(0xFF07040D), Offset(-0.5f, -1f), Offset(-0.7f, -2.1f), strokeWidth = 0.4f)
    drawLine(Color(0xFF07040D), Offset(0.5f, -1f), Offset(0.7f, -2.1f), strokeWidth = 0.4f)
}

private fun DrawScope.pumpkin(flicker: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFF9E2C).copy(alpha = 0.4f * flicker), Color.Transparent), Offset.Zero, 12f),
        12f, Offset.Zero
    )
    drawRoundRect(
        Color(0xFF3F6B22), Offset(-0.7f, -6.8f), Size(1.4f, 2.6f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.4f)
    )
    oval(Color(0xFFD9620E), -3.6f, 0f, 2.8f, 4.8f)
    oval(Color(0xFFD9620E), 3.6f, 0f, 2.8f, 4.8f)
    oval(Color(0xFFEB7A17), -1.9f, 0f, 2.9f, 5.1f)
    oval(Color(0xFFEB7A17), 1.9f, 0f, 2.9f, 5.1f)
    oval(Color(0xFFF58C25), 0f, 0f, 2.5f, 5.2f)
    val light = Color(0xFFFFE066).copy(alpha = 0.55f + 0.45f * flicker)
    fun triangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) = drawPath(
        Path().apply {
            moveTo(x0, y0)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        },
        light
    )
    triangle(-3f, -0.6f, -0.9f, -0.6f, -1.95f, -2.5f)
    triangle(3f, -0.6f, 0.9f, -0.6f, 1.95f, -2.5f)
    triangle(-0.5f, 0.6f, 0.5f, 0.6f, 0f, -0.5f)
    drawPath(
        Path().apply {
            moveTo(-3.6f, 1.2f)
            lineTo(-2.4f, 1.9f)
            lineTo(-1.8f, 1.3f)
            lineTo(-0.9f, 2.1f)
            lineTo(0f, 1.4f)
            lineTo(0.9f, 2.1f)
            lineTo(1.8f, 1.3f)
            lineTo(2.4f, 1.9f)
            lineTo(3.6f, 1.2f)
            quadraticTo(2.4f, 4f, 0f, 4f)
            quadraticTo(-2.4f, 4f, -3.6f, 1.2f)
            close()
        },
        light
    )
}

// --------------------------------------------------------------- Christmas

private fun DrawScope.drawChristmas(t: Float) {
    sky(listOf(Color(0xFF071530), Color(0xFF12305C), Color(0xFF2F5C8E)))
    stars(t, maxY = 0.6f)

    // Santa and his reindeer fly across every half a minute, a little higher or lower each time.
    val flight = t / 30f
    val across = fraction(flight)
    val level = 0.1f + hash(floor(flight).toInt() + 3) * 0.08f
    at(w * (-0.45f + across * 1.9f), h * level + sin(t * 1.3f) * unit * 2f, unit * 1.05f) { sleigh(t) }

    // Snow hills, a little house and fir trees.
    drawPath(
        Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.8f)
            cubicTo(w * 0.25f, h * 0.72f, w * 0.42f, h * 0.9f, w * 0.62f, h * 0.86f)
            cubicTo(w * 0.78f, h * 0.82f, w * 0.9f, h * 0.76f, w, h * 0.82f)
            lineTo(w, h)
            close()
        },
        Color(0xFFEAF2FB)
    )
    at(w * 0.9f, h * 0.84f, unit * 1.1f) { cottage(t) }
    at(w * 0.04f, h * 0.86f, unit * 1.5f) { firTree(t) }
    at(w * 0.14f, h * 0.84f, unit * 1.05f) { firTree(t + 1f) }
    at(w * 0.8f, h * 0.9f, unit * 0.95f) { firTree(t + 2f) }
    at(w * 0.97f, h * 0.93f, unit * 0.8f) { firTree(t + 3f) }

    // Snow falls over everything.
    Snow.forEach { s ->
        val y = fraction(s.y + t * (0.025f + s.speed * 0.04f)) * h
        val x = fraction(s.x + sin(t * 0.5f + s.phase * 6.28f) * 0.012f) * w
        drawCircle(Color.White.copy(alpha = 0.55f + s.size * 0.45f), (0.18f + s.size * 0.42f) * unit, Offset(x, y))
    }
}

private fun DrawScope.firTree(t: Float) {
    val base = Color(0xFF0F5132)
    drawRect(Color(0xFF4E342E), Offset(-1f, -2f), Size(2f, 3.5f))
    listOf(Triple(-1.5f, 8f, 8f), Triple(-6f, 6.4f, 7.5f), Triple(-10f, 4.6f, 7f)).forEach { (bottom, half, tall) ->
        drawPath(
            Path().apply {
                moveTo(-half, bottom)
                lineTo(half, bottom)
                lineTo(0f, bottom - tall)
                close()
            },
            base
        )
    }
    // Lights blink in turn, with a star on top.
    listOf(-4.5f to -3f, 4f to -3.4f, -2f to -5.4f, 2.6f to -7.4f, -3f to -9f, 0.8f to -11f, 5.6f to -2.2f, -6f to -2.2f)
        .forEachIndexed { i, (x, y) ->
            val on = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 2.2f + i * 1.7f))
            drawCircle(FestiveColors[i % FestiveColors.size].copy(alpha = on), 0.5f, Offset(x, y))
        }
    star(0f, -17.5f, 1.6f, Gold)
}

private fun DrawScope.star(cx: Float, cy: Float, radius: Float, color: Color) {
    drawPath(
        Path().apply {
            repeat(10) { i ->
                val r = if (i % 2 == 0) radius else radius * 0.45f
                val a = i / 10f * 2f * PI.toFloat() - PI.toFloat() / 2f
                val x = cx + cos(a) * r
                val y = cy + sin(a) * r
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        },
        color
    )
}

private fun DrawScope.cottage(t: Float) {
    drawRect(Color(0xFF6D4C41), Offset(-7f, -7f), Size(14f, 8f))
    drawPath(
        Path().apply {
            moveTo(-9f, -6.5f)
            lineTo(9f, -6.5f)
            lineTo(0f, -13.5f)
            close()
        },
        Color(0xFFF5F9FF)
    )
    val light = Color(0xFFFFD54F).copy(alpha = 0.85f + 0.15f * sin(t * 2f))
    drawRect(light, Offset(-5f, -5f), Size(3.2f, 3f))
    drawRect(light, Offset(1.8f, -5f), Size(3.2f, 3f))
    drawRect(Color(0xFF3E2723), Offset(-1.2f, -4f), Size(2.4f, 5f))
    // Smoke from the chimney.
    drawRect(Color(0xFF4E342E), Offset(3.5f, -13.5f), Size(2f, 4f))
    repeat(4) { i ->
        val rise = fraction(t * 0.18f + i * 0.25f)
        drawCircle(
            Color.White.copy(alpha = (1f - rise) * 0.5f), 0.7f + rise * 1.6f,
            Offset(4.5f + sin(rise * 7f) * 1.2f, -14.5f - rise * 11f)
        )
    }
}

/** Santa at (0, 0), facing right, with the reindeer in front. */
private fun DrawScope.sleigh(t: Float) {
    // Reins.
    drawLine(Color(0xFFFFD54F), Offset(3.2f, -3f), Offset(32f, -1.5f), strokeWidth = 0.22f)

    // Reindeer in pairs, the front one with the red nose.
    listOf(12f, 21f, 30f).forEachIndexed { i, x ->
        at(x, 0f, 1f) { reindeer(t + i * 0.45f, rudolph = i == 2) }
    }

    // Gift sack behind Santa.
    oval(Color(0xFF8D5A2B), -5f, -3.2f, 3f, 3.4f)
    drawLine(Color(0xFFFFD54F), Offset(-5.6f, -6.4f), Offset(-4.2f, -6.4f), strokeWidth = 0.5f)

    // Santa.
    oval(Color(0xFFD32F2F), 0f, -3.4f, 3f, 3.6f)
    drawRect(Color(0xFF212121), Offset(-2.9f, -2.8f), Size(5.8f, 0.8f))
    drawRect(Gold, Offset(-0.6f, -2.9f), Size(1.2f, 1f))
    drawCircle(Color(0xFFFFCCBC), 1.7f, Offset(0.6f, -7.6f))
    oval(Color.White, 0.8f, -6.2f, 1.6f, 1.9f)
    drawCircle(Color(0xFF212121), 0.22f, Offset(1.3f, -8f))
    drawPath(
        Path().apply {
            moveTo(-1.4f, -8.4f)
            lineTo(2.4f, -8.6f)
            quadraticTo(1f, -11.4f, -2.6f, -10.2f)
            close()
        },
        Color(0xFFD32F2F)
    )
    drawRoundRect(
        Color.White, Offset(-1.7f, -9f), Size(4.4f, 0.9f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.45f)
    )
    drawCircle(Color.White, 0.65f, Offset(-2.7f, -10.2f))

    // The sleigh itself.
    drawPath(
        Path().apply {
            moveTo(-8f, -2.4f)
            lineTo(5f, -2.4f)
            quadraticTo(7.4f, -2.4f, 7f, -5.2f)
            lineTo(9f, -5.2f)
            quadraticTo(9.6f, 1.6f, 4f, 1.6f)
            lineTo(-5f, 1.6f)
            quadraticTo(-8f, 1.6f, -8f, -2.4f)
            close()
        },
        Color(0xFFB71C1C)
    )
    drawLine(Gold, Offset(-7.6f, -1.8f), Offset(6.8f, -1.8f), strokeWidth = 0.4f)
    drawPath(
        Path().apply {
            moveTo(-7f, 3.4f)
            lineTo(7f, 3.4f)
            quadraticTo(10f, 3.4f, 10.4f, 1f)
        },
        Gold,
        style = Stroke(width = 0.5f, cap = StrokeCap.Round)
    )
    drawLine(Gold, Offset(-4f, 1.6f), Offset(-4f, 3.4f), strokeWidth = 0.45f)
    drawLine(Gold, Offset(4f, 1.6f), Offset(4f, 3.4f), strokeWidth = 0.45f)
}

private fun DrawScope.reindeer(t: Float, rudolph: Boolean) {
    val fur = Color(0xFF8D5A2B)
    val dark = Color(0xFF5D3A1A)
    val gallop = sin(t * 8f)
    val leg = { x: Float, phase: Float ->
        val swing = gallop * phase
        drawLine(dark, Offset(x, -1f), Offset(x + swing * 1.5f, 1.9f), strokeWidth = 0.5f, cap = StrokeCap.Round)
    }
    leg(-2.2f, 1f)
    leg(-1.2f, -1f)
    leg(1.2f, 1f)
    leg(2.2f, -1f)
    oval(fur, 0f, -2f, 3.4f, 1.6f)
    oval(Color(0xFFEFDCC4), -3.3f, -2.9f, 0.6f, 0.45f)
    // Neck and head.
    drawLine(fur, Offset(2.4f, -2.6f), Offset(3.9f, -5f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    oval(fur, 4.5f, -5.4f, 1.4f, 0.95f)
    drawCircle(Color(0xFF212121), 0.17f, Offset(4.6f, -5.8f))
    drawCircle(if (rudolph) Color(0xFFFF1744) else Color(0xFF212121), if (rudolph) 0.5f else 0.28f, Offset(5.9f, -5.3f))
    if (rudolph) drawCircle(Color(0x55FF1744), 1.2f + 0.3f * sin(t * 6f), Offset(5.9f, -5.3f))
    // Antlers.
    drawLine(dark, Offset(3.8f, -6.1f), Offset(2.9f, -8.4f), strokeWidth = 0.28f)
    drawLine(dark, Offset(3.3f, -7.4f), Offset(2.2f, -7.8f), strokeWidth = 0.22f)
    drawLine(dark, Offset(3f, -8.1f), Offset(3.9f, -8.9f), strokeWidth = 0.22f)
    drawLine(dark, Offset(4.5f, -6.2f), Offset(4.5f, -8.6f), strokeWidth = 0.28f)
    drawLine(dark, Offset(4.5f, -7.5f), Offset(5.5f, -8f), strokeWidth = 0.22f)
    // Harness.
    drawLine(Color(0xFFC62828), Offset(2.4f, -3.5f), Offset(2.9f, -0.8f), strokeWidth = 0.42f)
}

// ---------------------------------------------------------------- New Year

private fun DrawScope.drawNewYear(t: Float) {
    sky(listOf(Color(0xFF04050F), Color(0xFF0F1438), Color(0xFF2A2560)))
    stars(t, maxY = 0.75f)
    fireworks(t, 7)
    // City skyline with lit windows.
    val r = Random(21)
    var x = 0f
    var building = 0
    while (x < w) {
        val width = (5f + r.nextFloat() * 8f) * unit
        val height = h * (0.08f + r.nextFloat() * 0.13f)
        val top = h - height
        drawRect(Color(0xFF0A0B1E), Offset(x, top), Size(width + 1f, height))
        var wy = top + unit * 1.2f
        var row = 0
        while (wy < h - unit) {
            var wx = x + unit * 0.9f
            var col = 0
            while (wx < x + width - unit * 1.2f) {
                val seed = hash(building * 31 + row * 7 + col)
                if (seed > 0.45f) {
                    val on = sin(t * 0.25f + seed * 40f) > -0.5f
                    if (on) drawRect(Color(0xFFFFD77A).copy(alpha = 0.75f), Offset(wx, wy), Size(unit * 0.8f, unit * 0.9f))
                }
                wx += unit * 1.9f
                col++
            }
            wy += unit * 2.2f
            row++
        }
        x += width
        building++
    }
}

// --------------------------------------------------------------- Valentine

private fun DrawScope.drawValentine(t: Float) {
    sky(listOf(Color(0xFFFFE1EA), Color(0xFFFFB8CC), Color(0xFFF7799C)))
    // Big hearts in the corners, beating.
    val beat = 1f + 0.06f * sin(t * 3.4f).let { it * it }
    at(w * 0.07f, h * 0.84f, unit * 2.2f * beat) { heart(Color(0xFFD81B60)) }
    at(w * 0.94f, h * 0.2f, unit * 1.7f * beat) { heart(Color(0xFFE91E63)) }
    at(w * 0.92f, h * 0.87f, unit * 1.3f * beat) { heart(Color(0xFFF06292)) }
    at(w * 0.07f, h * 0.18f, unit * 1.2f * beat) { heart(Color(0xFFEC407A)) }
    // An arrow through the big heart.
    val a = Offset(w * 0.07f, h * 0.84f)
    drawLine(Color(0xFF7B1FA2), a + Offset(-unit * 12f, unit * 7f), a + Offset(unit * 12f, -unit * 7f), strokeWidth = unit * 0.5f)
    star(a.x + unit * 12f, a.y - unit * 7f, unit * 1.2f, Color(0xFFFFD54F))

    // Small hearts float up, swaying.
    Hearts.forEach { p ->
        val rise = fraction(p.y + t * (0.025f + p.speed * 0.035f))
        val x = p.x * w + sin(t * 0.9f + p.phase * 6.28f) * unit * 3f
        val y = h * 1.05f - rise * h * 1.15f
        val color = listOf(Color(0xFFE91E63), Color(0xFFFF4081), Color.White, Color(0xFFC2185B))[(p.phase * 4).toInt() % 4]
        at(x, y, unit * (0.22f + p.size * 0.4f)) { heart(color.copy(alpha = 0.9f * min(1f, (1f - rise) * 2.2f))) }
    }
}

private fun DrawScope.heart(color: Color) {
    drawPath(
        Path().apply {
            moveTo(0f, 7f)
            cubicTo(-11f, -1f, -7f, -8f, 0f, -3.4f)
            cubicTo(7f, -8f, 11f, -1f, 0f, 7f)
            close()
        },
        color
    )
}

// ------------------------------------------------------------ Vasario 16-oji

private fun DrawScope.drawIndependence(t: Float) {
    sky(listOf(Color(0xFFFFF8E1), Color(0xFFF1F7EC), Color.White))
    val sun = Offset(w * 0.9f, h * 0.14f)
    drawCircle(Brush.radialGradient(listOf(Tricolor.Yellow.copy(alpha = 0.55f), Color.Transparent), sun, unit * 26f), unit * 26f, sun)
    drawCircle(Tricolor.Yellow, unit * 7f, sun)

    // Gediminas' Tower on its hill, flag on top.
    at(w * 0.1f, h * 0.76f, unit * 1.05f) { gediminasTower(t) }

    // The flag's three colours, waving across the bottom of the screen.
    tricolorWave(t, h * 0.82f, h * 0.87f, Tricolor.Yellow, 0f)
    tricolorWave(t, h * 0.87f, h * 0.92f, Tricolor.Green, 0.25f)
    tricolorWave(t, h * 0.92f, h * 1.02f, Tricolor.Red, 0.5f)

    confetti(t, listOf(Tricolor.Yellow, Tricolor.Green, Tricolor.Red))
}

private fun DrawScope.tricolorWave(t: Float, top: Float, bottom: Float, color: Color, phase: Float) {
    val path = Path().apply {
        moveTo(0f, bottom + unit * 6f)
        var x = 0f
        val step = w / 24f
        lineTo(0f, top + wave(0f, t, phase))
        while (x <= w) {
            x += step
            lineTo(x, top + wave(x, t, phase))
        }
        lineTo(w, bottom + unit * 6f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.wave(x: Float, t: Float, phase: Float): Float =
    sin(x / w * 7f - t * 1.3f + phase) * unit * 2f + sin(x / w * 3f + t * 0.7f) * unit * 1.2f

private fun DrawScope.gediminasTower(t: Float) {
    drawPath(
        Path().apply {
            moveTo(-30f, 16f)
            cubicTo(-14f, 0f, -12f, -4f, -8f, -5f)
            lineTo(9f, -5f)
            cubicTo(14f, -3f, 18f, 4f, 32f, 16f)
            close()
        },
        Color(0xFF558B2F)
    )
    val brick = Color(0xFFB5533C)
    val brickDark = Color(0xFF8F3E2C)
    drawRect(brick, Offset(-6f, -24f), Size(12f, 20f))
    drawRect(brickDark, Offset(-6f, -17f), Size(12f, 0.5f))
    drawRect(brickDark, Offset(-6f, -11f), Size(12f, 0.5f))
    repeat(4) { i -> drawRect(brick, Offset(-6f + i * 3.4f, -26f), Size(1.8f, 2.2f)) }
    listOf(-3f to -21f, 1.4f to -21f, -3f to -15f, 1.4f to -15f, -0.8f to -9f).forEach { (x, y) ->
        drawRoundRect(
            Color(0xFF3E2723), Offset(x, y), Size(1.6f, 3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8f)
        )
    }
    at(0f, -26f, 1f) { wavingFlag(t) }
}

/** A pole standing at (0, 0) with the flag waving from its top. */
private fun DrawScope.wavingFlag(t: Float) {
    drawLine(Color(0xFF5D4037), Offset.Zero, Offset(0f, -14f), strokeWidth = 0.5f)
    val stripe = 1.5f
    listOf(Tricolor.Yellow, Tricolor.Green, Tricolor.Red).forEachIndexed { row, color ->
        val top = -14f + row * stripe
        drawPath(
            Path().apply {
                moveTo(0f, top)
                repeat(9) { i ->
                    val x = (i + 1) * 1.2f
                    lineTo(x, top + sin(x * 0.7f - t * 4f) * 0.5f * (x / 10f))
                }
                repeat(9) { i ->
                    val x = (9 - i) * 1.2f
                    lineTo(x, top + stripe + sin(x * 0.7f - t * 4f) * 0.5f * (x / 10f))
                }
                lineTo(0f, top + stripe)
                close()
            },
            color
        )
    }
}

// --------------------------------------------------------------- Kovo 11-oji

private fun DrawScope.drawMarch11(t: Float) {
    sky(listOf(Color(0xFF17386B), Color(0xFF5C93CF), Color(0xFFFBD9A0)))
    val sun = Offset(w * 0.5f, h * 0.84f)
    val rays = unit * (46f + 3f * sin(t * 0.6f))
    drawCircle(Brush.radialGradient(listOf(Color(0xCCFFE9A8), Color.Transparent), sun, rays), rays, sun)

    // The Three Crosses on their hill and the Cathedral belfry: the sights of Vilnius.
    at(w * 0.11f, h * 0.8f, unit * 1.05f, DrawScope::threeCrosses)
    at(w * 0.9f, h * 0.88f, unit * 1.15f, DrawScope::belfry)

    // The crowd with flags raised, as on the square in 1990.
    val spacing = unit * 3.2f
    val crowdY = h * 0.92f
    val people = (w / spacing).toInt() + 2
    repeat(people) { i ->
        if (i % 4 == 1) {
            val sway = sin(t * 0.9f + i) * 5f
            val x = i * spacing
            val y = crowdY + hash(i) * unit * 2f
            rotate(sway, Offset(x, y)) {
                at(x, y, unit * 1.05f) { wavingFlag(t + i * 0.7f) }
            }
        }
    }
    repeat(people) { i ->
        val bob = sin(t * 2f + i * 1.3f) * unit * 0.25f
        at(i * spacing, crowdY + hash(i) * unit * 2f + bob, unit) {
            oval(Color(0xFF14233F), 0f, 6f, 1.7f, 5f)
            drawCircle(Color(0xFF14233F), 1.15f, Offset.Zero)
        }
    }
    drawRect(Color(0xFF14233F), Offset(0f, crowdY + unit * 6f), Size(w, h))

    confetti(t, listOf(Tricolor.Yellow, Tricolor.Green, Tricolor.Red))
}

private fun DrawScope.threeCrosses() {
    drawPath(
        Path().apply {
            moveTo(-34f, 22f)
            cubicTo(-20f, 4f, -12f, -5f, 0f, -6f)
            cubicTo(12f, -5f, 20f, 6f, 36f, 22f)
            close()
        },
        Color(0xFF2F6B3A)
    )
    listOf(Triple(-7f, -5f, 16f), Triple(0f, -6f, 20f), Triple(7f, -5f, 16f)).forEach { (x, base, tall) ->
        drawRect(Color.White, Offset(x - 0.9f, base - tall), Size(1.8f, tall))
        drawRect(Color.White, Offset(x - tall * 0.24f, base - tall * 0.76f), Size(tall * 0.48f, 1.6f))
    }
}

private fun DrawScope.belfry() {
    val wall = Color(0xFFF5F1E6)
    val shade = Color(0xFFCFC8B6)
    val dark = Color(0xFF55606E)
    drawRect(wall, Offset(-8f, -14f), Size(16f, 26f))
    drawRect(shade, Offset(-8f, -14f), Size(16f, 1f))
    drawRect(wall, Offset(-6.4f, -25f), Size(12.8f, 11f))
    drawRect(shade, Offset(-6.4f, -25f), Size(12.8f, 1f))
    drawRect(wall, Offset(-4.8f, -35f), Size(9.6f, 10f))
    drawRect(shade, Offset(-4.8f, -35f), Size(9.6f, 0.8f))
    listOf(-4.5f to -9f, 2.5f to -9f, -3.4f to -21f, 1.6f to -21f).forEach { (x, y) ->
        drawRoundRect(
            dark, Offset(x, y), Size(1.9f, 4.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.95f)
        )
    }
    // The clock.
    drawCircle(Color.White, 2.6f, Offset(0f, -30f))
    drawCircle(dark, 2.6f, Offset(0f, -30f), style = Stroke(width = 0.3f))
    drawLine(dark, Offset(0f, -30f), Offset(0f, -31.9f), strokeWidth = 0.3f)
    drawLine(dark, Offset(0f, -30f), Offset(1.3f, -29.4f), strokeWidth = 0.3f)
    // The spire and its cross.
    drawPath(
        Path().apply {
            moveTo(-4.8f, -35f)
            lineTo(4.8f, -35f)
            lineTo(0f, -44f)
            close()
        },
        Color(0xFF3F7A6B)
    )
    drawLine(Gold, Offset(0f, -44f), Offset(0f, -48f), strokeWidth = 0.45f)
    drawLine(Gold, Offset(-1f, -46.6f), Offset(1f, -46.6f), strokeWidth = 0.45f)
}

// ----------------------------------------------------------------- Joninės

private fun DrawScope.drawJonines(t: Float) {
    sky(listOf(Color(0xFF0C1A3E), Color(0xFF3A2A5E), Color(0xFFC96B3B), Color(0xFFE8A04E)))
    stars(t, maxY = 0.4f)
    val moon = Offset(w * 0.1f, h * 0.15f)
    drawCircle(Brush.radialGradient(listOf(Color(0x55FFF3C4), Color.Transparent), moon, unit * 18f), unit * 18f, moon)
    drawCircle(Color(0xFFFFF3C4), unit * 7f, moon)

    // Dark forest along the shore.
    val forest = Color(0xFF0E1F1A)
    drawRect(forest, Offset(0f, h * 0.73f), Size(w, h * 0.05f))
    val r = Random(31)
    var x = -unit * 2f
    while (x < w + unit * 4f) {
        val tall = h * (0.08f + r.nextFloat() * 0.1f)
        val half = unit * (1.6f + r.nextFloat() * 1.6f)
        drawPath(
            Path().apply {
                moveTo(x - half, h * 0.75f)
                lineTo(x, h * 0.75f - tall)
                lineTo(x + half, h * 0.75f)
                close()
            },
            forest
        )
        x += half * 1.25f
    }

    // The lake, with the fire's light on it.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2C2A52), Color(0xFF0D1730)), h * 0.77f, h), Offset(0f, h * 0.77f), Size(w, h * 0.23f))
    repeat(9) { i ->
        val y = h * 0.79f + i * unit * 2f
        val half = unit * (3f + i * 0.7f) * (1f + 0.25f * sin(t * 2.4f + i))
        drawLine(
            Color(0xFFFFA040).copy(alpha = 0.42f - i * 0.035f), Offset(w * 0.86f - half, y), Offset(w * 0.86f + half, y),
            strokeWidth = unit * 0.35f, cap = StrokeCap.Round
        )
    }

    // Bonfire on the far shore, on the right.
    at(w * 0.86f, h * 0.76f, unit * 1.25f) { bonfire(t) }

    // Wreaths with candles float slowly across the lake.
    repeat(3) { i ->
        val pass = fraction(t * 0.012f + i / 3f)
        at(w * (-0.05f + pass * 1.1f), h * (0.84f + i * 0.05f) + sin(t + i) * unit * 0.4f, unit * (0.9f + i * 0.2f)) {
            wreath(t + i)
        }
    }

    // The fern flower blooms in the corner.
    at(w * 0.07f, h * 0.87f, unit * 1.1f) { fernFlower(t) }

    // Fireflies.
    Fireflies.forEach { f ->
        val x = f.x * w + sin(t * 0.6f * (0.5f + f.speed) + f.phase * 6f) * unit * 5f
        val y = (0.45f + f.y * 0.32f) * h + cos(t * 0.5f + f.phase * 9f) * unit * 3f
        val on = (0.5f + 0.5f * sin(t * 2f + f.phase * 6.28f)).let { it * it }
        drawCircle(Color(0x55D4FF6B).copy(alpha = on * 0.35f), unit * 1.1f, Offset(x, y))
        drawCircle(Color(0xFFE6FF8A).copy(alpha = on), unit * 0.3f, Offset(x, y))
    }
}

private fun DrawScope.bonfire(t: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0x66FF9E2C), Color.Transparent), Offset(0f, -8f), 26f),
        26f, Offset(0f, -8f)
    )
    val wood = Color(0xFF3E2317)
    drawLine(wood, Offset(-8f, 1f), Offset(7f, -3f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(wood, Offset(8f, 1f), Offset(-7f, -3f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    flame(t, 7f, 18f, Color(0xFFE64A19), 0f)
    flame(t, 5f, 14f, Color(0xFFFF8F00), 1.5f)
    flame(t, 3f, 9f, Color(0xFFFFEB3B), 3f)
    Sparks.forEach { s ->
        val rise = fraction(t * (0.2f + s.speed * 0.3f) + s.phase)
        drawCircle(
            Color(0xFFFFC107).copy(alpha = 1f - rise), 0.2f + s.size * 0.25f,
            Offset((s.x - 0.5f) * 8f + sin(rise * 8f + s.phase * 6f) * 2.5f, -8f - rise * 30f)
        )
    }
}

private fun DrawScope.flame(t: Float, half: Float, tall: Float, color: Color, phase: Float) {
    val tip = sin(t * 6f + phase) * half * 0.4f
    val sway = sin(t * 4.3f + phase * 2f) * half * 0.25f
    val top = tall * (1f + 0.1f * sin(t * 9f + phase))
    drawPath(
        Path().apply {
            moveTo(-half, 0f)
            cubicTo(-half * 1.2f, -top * 0.4f, -half * 0.3f + sway, -top * 0.6f, tip, -top)
            cubicTo(half * 0.4f + sway, -top * 0.6f, half * 1.2f, -top * 0.4f, half, 0f)
            close()
        },
        color
    )
}

private fun DrawScope.wreath(t: Float) {
    oval(Color(0x33FFE082), 0f, 0.6f, 7f, 1.8f)
    repeat(14) { i ->
        val a = i / 14f * 2f * PI.toFloat()
        val p = Offset(cos(a) * 3.4f, sin(a) * 1.2f)
        drawCircle(Color(0xFF558B2F), 0.8f, p)
        if (i % 3 == 0) drawCircle(FestiveColors[i % FestiveColors.size], 0.5f, p)
    }
    drawRect(Color(0xFFFFF8E1), Offset(-0.35f, -2.6f), Size(0.7f, 2.4f))
    flameTip(t)
}

private fun DrawScope.flameTip(t: Float) {
    val glow = 0.8f + 0.2f * sin(t * 8f)
    drawCircle(Color(0x66FFB300).copy(alpha = 0.4f * glow), 1.6f, Offset(0f, -3.2f))
    oval(Color(0xFFFFC107), 0f, -3.2f, 0.4f, 0.8f * glow)
}

private fun DrawScope.fernFlower(t: Float) {
    // Fern fronds.
    listOf(-1f, 1f).forEach { side ->
        drawPath(
            Path().apply {
                moveTo(0f, 6f)
                quadraticTo(side * 5f, 1f, side * 12f, 3f)
            },
            Color(0xFF2E7D32),
            style = Stroke(width = 0.5f)
        )
        repeat(6) { i ->
            val f = (i + 1) / 7f
            val x = side * 12f * f
            val y = 6f - 5f * f + 1.5f * f * f
            drawLine(Color(0xFF388E3C), Offset(x, y), Offset(x + side * 1.2f, y - 2f), strokeWidth = 0.4f)
            drawLine(Color(0xFF388E3C), Offset(x, y), Offset(x + side * 1.2f, y + 1.7f), strokeWidth = 0.4f)
        }
    }
    drawLine(Color(0xFF2E7D32), Offset(0f, 6f), Offset(0f, -4f), strokeWidth = 0.6f)
    // The flower that, as the legend goes, blooms only on this night.
    val open = 0.72f + 0.28f * sin(t * 0.8f)
    drawCircle(Brush.radialGradient(listOf(Color(0xAAFFF176), Color.Transparent), Offset(0f, -6f), 15f * open), 15f * open, Offset(0f, -6f))
    repeat(8) { i ->
        rotate(i * 45f + t * 8f, Offset(0f, -6f)) {
            oval(Color(0xFFFFEE58), 0f, -6f - 3.2f * open, 1.2f, 3.2f * open)
        }
    }
    drawCircle(Color.White, 1.3f, Offset(0f, -6f))
}

// ------------------------------------------------------------- Mindauginės

private fun DrawScope.drawMindaugines(t: Float) {
    sky(listOf(Color(0xFFFFF3C9), Color(0xFFFFDE85), Color(0xFFF2AE3B)))
    // Sun rays turning slowly behind everything.
    val center = Offset(w * 0.5f, h * 0.02f)
    val reach = w + h
    rotate(t * 2f, center) {
        repeat(18) { i ->
            val a = i / 18f * 2f * PI.toFloat()
            val b = a + 0.1f
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(center.x + cos(a) * reach, center.y + sin(a) * reach)
                    lineTo(center.x + cos(b) * reach, center.y + sin(b) * reach)
                    close()
                },
                Color.White.copy(alpha = 0.16f)
            )
        }
    }

    // Tricolour ribbons down both sides.
    listOf(0.015f, 0.965f).forEach { xFraction ->
        listOf(Tricolor.Yellow, Tricolor.Green, Tricolor.Red).forEachIndexed { i, color ->
            val left = w * xFraction + i * unit * 1.4f
            drawPath(
                Path().apply {
                    moveTo(left + sin(t) * unit * 0.3f, 0f)
                    var y = 0f
                    while (y <= h) {
                        y += h / 20f
                        lineTo(left + sin(y / h * 12f - t * 1.2f + i * 0.4f) * unit * 0.6f, y)
                    }
                    lineTo(left + unit * 1.4f + sin(t * 1.2f) * unit * 0.2f, h)
                    y = h
                    while (y >= 0f) {
                        y -= h / 20f
                        lineTo(left + unit * 1.4f + sin(y / h * 12f - t * 1.2f + i * 0.4f) * unit * 0.6f, y)
                    }
                    close()
                },
                color
            )
        }
    }

    // Castle hills, then the crown of King Mindaugas in both top corners.
    drawPath(
        Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.88f)
            cubicTo(w * 0.3f, h * 0.8f, w * 0.65f, h * 0.96f, w, h * 0.86f)
            lineTo(w, h)
            close()
        },
        Color(0xFF3F6B2A)
    )
    at(w * 0.13f, h * 0.86f, unit * 0.95f) { castle(t) }
    at(w * 0.87f, h * 0.87f, unit * 0.75f) { castle(t + 1f) }

    val tilt = sin(t * 0.7f) * 3f
    rotate(-8f + tilt, Offset(w * 0.12f, h * 0.2f)) { at(w * 0.12f, h * 0.2f, unit * 1.6f) { crown(t) } }
    rotate(8f - tilt, Offset(w * 0.88f, h * 0.2f)) { at(w * 0.88f, h * 0.2f, unit * 1.6f) { crown(t + 2f) } }

    // Gold dust falling.
    Sparks.forEach { s ->
        val fall = fraction(s.y + t * (0.03f + s.speed * 0.03f))
        val pulse = 0.5f + 0.5f * sin(t * 3f + s.phase * 6.28f)
        star(s.x * w, fall * h, unit * (0.4f + s.size * 0.8f) * (0.6f + 0.4f * pulse), Color.White.copy(alpha = 0.5f + 0.5f * pulse))
    }
}

private fun DrawScope.crown(t: Float) {
    val gold = Color(0xFFE6A800)
    val shade = Color(0xFFB98500)
    drawPath(
        Path().apply {
            moveTo(-11f, 5f)
            lineTo(-13f, -6f)
            lineTo(-6.5f, -1f)
            lineTo(-6f, -9f)
            lineTo(-2.6f, -2.5f)
            lineTo(0f, -11f)
            lineTo(2.6f, -2.5f)
            lineTo(6f, -9f)
            lineTo(6.5f, -1f)
            lineTo(13f, -6f)
            lineTo(11f, 5f)
            close()
        },
        Brush.linearGradient(listOf(Color(0xFFFFE27A), gold, shade), Offset(-12f, -10f), Offset(12f, 6f))
    )
    drawRect(shade, Offset(-11f, 3f), Size(22f, 3.2f))
    listOf(-7f to Tricolor.Red, -2.4f to Tricolor.Green, 2.4f to Color(0xFF1E88E5), 7f to Tricolor.Red).forEach { (x, color) ->
        drawCircle(color, 1f, Offset(x, 4.6f))
    }
    listOf(-13f to -6f, -6f to -9f, 0f to -11f, 6f to -9f, 13f to -6f).forEach { (x, y) ->
        drawCircle(Color(0xFFFFF3C4), 1f, Offset(x, y))
    }
    // A glint runs along the crown.
    val glint = sin(t * 1.4f) * 11f
    star(glint, -2f, 2f * (0.4f + 0.6f * (0.5f + 0.5f * cos(t * 2.8f))), Color.White)
}

private fun DrawScope.castle(t: Float) {
    val stone = Color(0xFF8D6E63)
    val dark = Color(0xFF5D4037)
    drawRect(stone, Offset(-14f, -9f), Size(28f, 10f))
    drawRect(stone, Offset(-17f, -17f), Size(7f, 18f))
    drawRect(stone, Offset(10f, -17f), Size(7f, 18f))
    drawRect(stone, Offset(-4f, -22f), Size(8f, 23f))
    repeat(7) { i -> drawRect(stone, Offset(-14f + i * 4.2f, -11f), Size(2.4f, 2.2f)) }
    drawRect(stone, Offset(-17f, -19f), Size(2f, 2f))
    drawRect(stone, Offset(-12f, -19f), Size(2f, 2f))
    drawRect(stone, Offset(10f, -19f), Size(2f, 2f))
    drawRect(stone, Offset(15f, -19f), Size(2f, 2f))
    drawRoundRect(
        dark, Offset(-2.2f, -5f), Size(4.4f, 6f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f)
    )
    drawRect(dark, Offset(-1f, -17f), Size(2f, 3f))
    drawLine(dark, Offset(0f, -22f), Offset(0f, -30f), strokeWidth = 0.4f)
    listOf(Tricolor.Yellow, Tricolor.Green, Tricolor.Red).forEachIndexed { row, color ->
        val wobble = sin(t * 3f + row) * 0.3f
        drawRect(color, Offset(0.2f, -30f + row * 1f + wobble), Size(6f, 1f))
    }
}

// ---------------------------------------------------------------- Birthday

private fun DrawScope.drawBirthday(t: Float) {
    sky(listOf(Color(0xFF2A1A5E), Color(0xFF7B3FA0), Color(0xFFF2709C)))
    stars(t, maxY = 0.45f)
    fireworks(t, 5)

    // Cake in the corner, candles flickering.
    at(w * 0.1f, h * 0.9f, unit * 1.25f) { cake(t) }

    // Balloons rise along the sides, and now and then across the middle.
    Balloons.forEachIndexed { i, b ->
        val rise = fraction(b.y + t * (0.018f + b.speed * 0.02f))
        val side = if (i % 3 == 2) b.x else if (i % 3 == 0) b.x * 0.2f else 0.8f + b.x * 0.2f
        val x = side * w + sin(t * 0.7f + b.phase * 6.28f) * unit * 2.5f
        val y = h * 1.15f - rise * h * 1.35f
        at(x, y, unit * (0.8f + b.size * 0.55f)) { balloon(t + i, FestiveColors[i % FestiveColors.size]) }
    }

    confetti(t, FestiveColors)
}

private fun DrawScope.balloon(t: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(0f, 5.4f)
            quadraticTo(sin(t * 1.5f) * 1.4f, 9f, 0f, 12f)
            quadraticTo(-sin(t * 1.5f) * 1.4f, 15f, 0f, 18f)
        },
        Color.White.copy(alpha = 0.7f),
        style = Stroke(width = 0.14f)
    )
    drawPath(
        Path().apply {
            moveTo(-0.7f, 5.8f)
            lineTo(0.7f, 5.8f)
            lineTo(0f, 4.6f)
            close()
        },
        color
    )
    oval(color, 0f, 0f, 4f, 5f)
    oval(Color.White.copy(alpha = 0.45f), -1.4f, -2f, 0.9f, 1.5f)
}

private fun DrawScope.cake(t: Float) {
    val corner = androidx.compose.ui.geometry.CornerRadius(1.2f)
    drawRoundRect(Color(0xFFFFF1F5), Offset(-14f, -8f), Size(28f, 10f), corner)
    drawRoundRect(Color(0xFFF8BBD0), Offset(-10f, -16f), Size(20f, 9f), corner)
    drawRoundRect(Color(0xFF6A1B9A), Offset(-16f, 1.4f), Size(32f, 1.8f), corner)
    // Icing drips.
    listOf(-12f, -7f, -2f, 3f, 8f, 12f).forEachIndexed { i, x ->
        oval(Color(0xFFEC407A), x, -8f, 1.6f, 1.5f + (i % 2))
    }
    listOf(-7f, -2f, 3f, 7f).forEach { x -> oval(Color.White, x, -16f, 1.5f, 1.2f) }
    listOf(-6f, 0f, 6f).forEachIndexed { i, x ->
        drawRect(FestiveColors[i + 1], Offset(x - 0.5f, -21f), Size(1f, 5f))
        val glow = 0.8f + 0.2f * sin(t * (7f + i) + i)
        drawCircle(Color(0x66FFB300), 2f * glow, Offset(x, -22.4f))
        oval(Color(0xFFFFC107), x, -22.4f, 0.55f, 1.2f * glow)
    }
}
