package com.sugarbridge.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Настроение Тэнси-тян: по уровню сахара или «спит», пока данных нет. */
enum class Mood { HAPPY, LOW, HIGH, SLEEPY }

// Контуры в системе координат 108×108 — те же, что в ic_launcher_foreground.xml
private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val DROP = path("M54,33C57.5,40 73,50 73,66A19,19 0,1 1,35 66C35,50 50.5,40 54,33Z")
private val WING_LEFT = path("M39,56C35,48 27,43.5 20.5,45C21.5,48.5 23,50.5 25.5,52C22,53.5 21.5,57 24,59.5C21.5,61.5 22.5,65.5 26.5,66C27.5,70 32.5,71.5 37,68.5Z")
private val WING_RIGHT = path("M69,56C73,48 81,43.5 87.5,45C86.5,48.5 85,50.5 82.5,52C86,53.5 86.5,57 84,59.5C86.5,61.5 85.5,65.5 81.5,66C80.5,70 75.5,71.5 71,68.5Z")
private val FEATHERS_LEFT = path("M36.5,56.5C32,53 28,52 25.5,52.2M36.5,61.5C31.5,59.5 27.5,59.5 24.5,59.6M37,66C33,65.6 29.5,66 26.8,66")
private val FEATHERS_RIGHT = path("M71.5,56.5C76,53 80,52 82.5,52.2M71.5,61.5C76.5,59.5 80.5,59.5 83.5,59.6M71,66C75,65.6 78.5,66 81.2,66")
private val SMILE = path("M51.5,71Q54,73.6 56.5,71")
private val WORRIED = path("M51.2,72.8Q54,70.6 56.8,72.8")
private val SLEEPY_EYES = path("M45.4,66.4Q47.5,68.4 49.6,66.4M58.4,66.4Q60.5,68.4 62.6,66.4")
private val BROWS = path("M45.2,60.6L49.4,61.8M62.8,60.6L58.6,61.8")

private val Ink = Color(0xFF4A2B4F)

@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val motion = rememberInfiniteTransition(label = "mascot")
    val bob by motion.animateFloat(
        initialValue = -1.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val flap by motion.animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(if (mood == Mood.SLEEPY) 2600 else 900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flap",
    )
    val glow by motion.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow",
    )
    Canvas(modifier.size(size).graphicsLayer()) {   // свой слой: анимация не перерисовывает карточку
        val k = this.size.minDimension / 108f
        scale(k, k, pivot = Offset.Zero) {
            translate(top = bob) { drawAngel(mood, flap, glow) }
        }
    }
}

private fun DrawScope.drawAngel(mood: Mood, flap: Float, glow: Float) {
    // крылья машут вокруг места, где крепятся к капельке
    rotate(-flap, pivot = Offset(38f, 60f)) { drawWing(WING_LEFT, FEATHERS_LEFT) }
    rotate(flap, pivot = Offset(70f, 60f)) { drawWing(WING_RIGHT, FEATHERS_RIGHT) }

    // нимб с пульсирующим свечением
    val halo = Size(24f, 7.6f)
    val haloAt = Offset(42f, 22.2f)
    drawOval(Color(0xFFFFE7A3).copy(alpha = glow * 0.6f), haloAt, halo, style = Stroke(5.5f))
    drawOval(Color(0xFFFFC23D), haloAt, halo, style = Stroke(2.8f))
    drawOval(Color(0xFFFFF4CC), haloAt, halo, style = Stroke(0.9f))

    // капелька: верх, низ градиента и контур
    val (top, bottom, outline) = when (mood) {
        Mood.LOW -> Triple(Color(0xFFD9D4FF), Color(0xFF9C95F5), Color(0xFF8079E6))    // побледнела
        Mood.HIGH -> Triple(Color(0xFFFFD2BF), Color(0xFFFF7E6B), Color(0xFFF0624F))   // раскраснелась
        else -> Triple(Color(0xFFFFC6DD), Color(0xFFFF6BA1), Color(0xFFEC5795))
    }
    drawPath(DROP, Brush.verticalGradient(listOf(top, bottom), startY = 33f, endY = 85f))
    drawPath(DROP, outline, style = Stroke(1.2f))
    rotate(30f, pivot = Offset(45.5f, 55f)) {
        drawOval(Color.White.copy(alpha = 0.75f), Offset(42.7f, 49.4f), Size(5.6f, 11.2f))
    }
    drawCircle(Color.White.copy(alpha = 0.85f), 1.3f, Offset(49.5f, 47.5f))

    // мордочка
    if (mood == Mood.SLEEPY) {
        drawPath(SLEEPY_EYES, Ink, style = Stroke(1.4f, cap = StrokeCap.Round))
    } else {
        drawOval(Ink, Offset(45.2f, 62.9f), Size(4.6f, 6.2f))
        drawOval(Ink, Offset(58.2f, 62.9f), Size(4.6f, 6.2f))
        drawCircle(Color.White, 0.9f, Offset(48.3f, 64.8f))
        drawCircle(Color.White, 0.9f, Offset(61.3f, 64.8f))
    }
    val blush = if (mood == Mood.HIGH) 0.75f else 0.45f
    drawOval(Color(0xFFFF5E9E).copy(alpha = blush), Offset(39.4f, 69.2f), Size(6.4f, 3.6f))
    drawOval(Color(0xFFFF5E9E).copy(alpha = blush), Offset(62.2f, 69.2f), Size(6.4f, 3.6f))
    when (mood) {
        Mood.HAPPY -> drawPath(SMILE, Ink, style = Stroke(1.5f, cap = StrokeCap.Round))
        Mood.SLEEPY -> drawCircle(Ink, 1.1f, Offset(54f, 72f))
        Mood.LOW -> {
            drawPath(WORRIED, Ink, style = Stroke(1.5f, cap = StrokeCap.Round))
            drawPath(BROWS, Ink, style = Stroke(1.1f, cap = StrokeCap.Round))
        }
        Mood.HIGH -> {
            drawOval(Ink, Offset(52.4f, 70.2f), Size(3.2f, 3.6f))
            // капелька пота
            val sweat = Path().apply {
                moveTo(70f, 50f)
                quadraticTo(72.8f, 54.4f, 70f, 56f)
                quadraticTo(67.2f, 54.4f, 70f, 50f)
            }
            drawPath(sweat, Color(0xFF9ED2FF))
        }
    }
}

private fun DrawScope.drawWing(wing: Path, feathers: Path) {
    drawPath(wing, Color.White)
    drawPath(wing, Color(0xFFC3ABFF), style = Stroke(1.2f, join = StrokeJoin.Round))
    drawPath(feathers, Color(0xFFD9CAFF), style = Stroke(0.9f, cap = StrokeCap.Round))
}
