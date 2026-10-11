package com.sugarbridge.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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

/** Tenshi-chan's mood: follows the glucose level, or "sleepy" while there is no data yet. */
enum class Mood { HAPPY, LOW, HIGH, SLEEPY }

// Shapes in the 108×108 launcher icon grid, the same as in ic_launcher_foreground.xml (generated together)
private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val WING_LEFT = path("M41.0,64.0 L38.5,55.9 L23.3,45.4 L16.0,43.0 L19.7,49.8 L32.5,62.9ZM39.0,70.0 L33.1,64.2 L16.0,63.8 L9.0,66.0 L15.2,70.0 L31.7,74.1ZM41.0,76.0 L33.9,75.5 L22.4,84.7 L19.0,90.0 L25.2,89.1 L38.4,82.6Z")
private val WING_RIGHT = path("M67,64L69.5,55.9L84.7,45.4L92,43L88.3,49.8L75.5,62.9ZM69,70L74.9,64.2L92,63.8L99,66L92.8,70L76.3,74.1ZM67,76L74.1,75.5L85.6,84.7L89,90L82.8,89.1L69.6,82.6Z")
private val WING_SHINE_LEFT = path("M37.0,58.6L20.2,45.6M33.2,67.5L13.6,65.9M35.8,77.6L22.0,87.4")
private val WING_SHINE_RIGHT = path("M71,58.6L87.8,45.6M74.8,67.5L94.4,65.9M72.2,77.6L86,87.4")
private val BACK_HAIR = path("M54,27C37,27 28,39 28,55C28,62 29,68 26,75L32,72L32,78L37,73L39,77L41,72L67,72L69,77L71,73L76,78L76,72L82,75C79,68 80,62 80,55C80,39 71,27 54,27Z")
private val BLOUSE = path("M35,99C34,90 41,83 50,81L58,81C67,83 74,90 73,99Q54,103 35,99Z")
private val PINAFORE = path("M41,99C41,93 44,88 48,86L60,86C64,88 67,93 67,99Q54,101.5 41,99Z")
private val COLLAR = path("M50,81L54,86.5L58,81")
private val RIBBON = path("M54,84.5C50,81.5 46,82.5 47,85.5C48,88.5 51,87.5 54,84.5ZM54,84.5C58,81.5 62,82.5 61,85.5C60,88.5 57,87.5 54,84.5Z")
private val RIBBON_TAILS = path("M53.4,85.5L51,92L53.2,91L54,93L54.8,91L57,92L54.6,85.5Z")
private val FACE = path("M36,54C36,44 44,40 54,40C64,40 72,44 72,54C72,64 68,72 62,76C59,78 56,79 54,79C52,79 49,78 46,76C40,72 36,64 36,54Z")
private val BLUSH_LINES = path("M38.9,67.5L38.1,69.3M40.9,67.5L40.1,69.3M42.9,67.5L42.1,69.3M65.9,67.5L65.1,69.3M67.9,67.5L67.1,69.3M69.9,67.5L69.1,69.3")
private val LASHES = path("M40.2,60.6Q40.9,56.3 45.3,56.2Q49.6,56.3 50.5,59.9M57.5,59.9Q58.4,56.3 62.7,56.2Q67.1,56.3 67.8,60.6M40.4,59.7L38.8,58.7M67.6,59.7L69.2,58.7")
private val SMILE = path("M51.2,71.1Q52.6,73 54,71.5Q55.4,73 56.8,71.1")
private val SIDE_LOCKS = path("M36,46C32,54 31,63 33,75L35.4,70L37.6,74C38.4,66 39.2,58 39.2,51C39.2,49 38,47 36,46ZM72,46C76,54 77,63 75,75L72.6,70L70.4,74C69.6,66 68.8,58 68.8,51C68.8,49 70,47 72,46Z")
private val BANGS = path("M30.5,59C28.5,41 39,28.5 54,28.5C69,28.5 79.5,41 77.5,59C75.5,54 73.5,50.5 70.5,47.5L69.5,55.5C66.5,51.5 64,48.5 61,46.5L61.8,53.5C59,50.5 57,47.5 55.5,45.5L54,57C52,51.5 50,48 47.5,46L46.6,53.5C44.6,49.5 42.6,48 40.2,47L39,54.5C36,51.5 33.5,54.5 30.5,59Z")
private val HAIR_SHINE = path("M40,36.5Q54,30.5 68,36.5")
private val BOW_LEFT = path("M62,25C54,12.5 40.5,9.5 37.5,16.5C34.5,24 43,33 62,25Z")
private val BOW_RIGHT = path("M62,25C69.5,11 84.5,8 87.5,15C90.5,22.5 81,32 62,25Z")
private val BOW_FOLDS = path("M62,25C54.5,19.5 47,18.5 42,19.8M62,25C69.5,18.5 77.5,17 83,18.5")
private val BOW_KNOT = path("M58.6,22.4Q62,20.8 65.4,22.4L65.4,27.6Q62,29.2 58.6,27.6Z")

// Mood details
private val WOBBLY = path("M51.2,72.5Q52.6,71.1 54,72.2Q55.4,71.1 56.8,72.5")
private val WORRIED_BROWS = path("M41.5,54.4Q44.5,53.8 48.2,52M66.5,54.4Q63.5,53.8 59.8,52")
private val CLOSED_EYES = path("M40.6,61.3Q45.3,65.7 50,61.3M58,61.3Q62.7,65.7 67.4,61.3M40.8,61.5L39.2,60.5M67.2,61.5L68.8,60.5")
private val SNOOZE = path("M84,36L88,36L84,40L88,40M90,29L93,29L90,32L93,32")
private val SWEAT = path("M79,40C79,40 82.5,44.5 82.5,46.5A3.5,3.5 0,1 1,75.5 46.5C75.5,44.5 79,40 79,40Z")

private val Ink = Color(0xFF22264A)
private val Mouth = Color(0xFFB24C6B)
private val HairLine = Color(0xFF3C86D2)
private val Hair = Brush.verticalGradient(listOf(Color(0xFFB9E6FF), Color(0xFF5FA9EA)), startY = 27f, endY = 80f)
private val Iris = Brush.verticalGradient(
    0f to Color(0xFF1D4C9E), 0.55f to Color(0xFF3A8BE4), 1f to Color(0xFFA8E6FF),
    startY = 56.5f, endY = 68.3f,
)
private val Ice = Brush.verticalGradient(listOf(Color(0xFFF2FCFF), Color(0xFFBDEBFF)), startY = 40f, endY = 92f)
private val Bow = Brush.verticalGradient(listOf(Color(0xFF6DB2FF), Color(0xFF2367CF)), startY = 11f, endY = 31f)
private val Dress = Brush.verticalGradient(listOf(Color(0xFF4A93E6), Color(0xFF2A62C4)), startY = 86f, endY = 100f)

@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val motion = rememberInfiniteTransition(label = "mascot")
    val bob by motion.animateFloat(
        initialValue = -1.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    // the ice wings sway a little and glint; slower when she is sleepy
    val sway by motion.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(if (mood == Mood.SLEEPY) 2600 else 1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val glint by motion.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glint",
    )
    // a quick blink every few seconds
    val blink by motion.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 3900
                0.1f at 4000
                1f at 4100
            },
        ),
        label = "blink",
    )
    Canvas(modifier.size(size).graphicsLayer()) {   // own layer: the animation does not redraw the card
        val k = this.size.minDimension / 108f
        scale(k, k, pivot = Offset.Zero) {
            translate(top = bob) { drawFairy(mood, sway, glint, blink) }
        }
    }
}

private fun DrawScope.drawFairy(mood: Mood, sway: Float, glint: Float, blink: Float) {
    // ice wings, swaying around the shoulders
    rotate(-sway, pivot = Offset(40f, 70f)) { drawWing(WING_LEFT, WING_SHINE_LEFT, glint) }
    rotate(sway, pivot = Offset(68f, 70f)) { drawWing(WING_RIGHT, WING_SHINE_RIGHT, glint) }

    drawPath(BACK_HAIR, Hair)
    drawPath(BACK_HAIR, HairLine, style = Stroke(1f, join = StrokeJoin.Round))

    // neck, blouse, blue pinafore and the red ribbon
    drawRect(Color(0xFFF6D5C6), Offset(50f, 72f), Size(8f, 10f))
    drawPath(BLOUSE, Color.White)
    drawPath(BLOUSE, Color(0xFFB9CCE6), style = Stroke(1f))
    drawPath(PINAFORE, Dress)
    drawPath(PINAFORE, Color(0xFF1D4FA0), style = Stroke(0.9f))
    drawPath(COLLAR, Color(0xFFB9CCE6), style = Stroke(0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(RIBBON_TAILS, Color(0xFFC62828))
    drawPath(RIBBON, Color(0xFFE53935))
    drawPath(RIBBON, Color(0xFFB71C1C), style = Stroke(0.5f))
    drawCircle(Color(0xFFB71C1C), 1.6f, Offset(54f, 84.6f))

    // face: pale when low, flushed when high
    val skin = when (mood) {
        Mood.LOW -> Color(0xFFF3EEF6)
        Mood.HIGH -> Color(0xFFFFE2D8)
        else -> Color(0xFFFFF1E8)
    }
    drawPath(FACE, skin)
    drawPath(FACE, Color(0xFFE8B9A6), style = Stroke(0.9f))

    val blush = when (mood) {
        Mood.HIGH -> 0.85f
        Mood.LOW -> 0.25f
        else -> 0.55f
    }
    drawOval(Color(0xFFFF8FB8).copy(alpha = blush), Offset(36.9f, 66.7f), Size(7.2f, 3.8f))
    drawOval(Color(0xFFFF8FB8).copy(alpha = blush), Offset(63.9f, 66.7f), Size(7.2f, 3.8f))
    drawPath(BLUSH_LINES, Color(0xFFFF7FAE).copy(alpha = 0.8f), style = Stroke(0.6f, cap = StrokeCap.Round))

    if (mood == Mood.SLEEPY) {
        drawPath(CLOSED_EYES, Ink, style = Stroke(1.6f, cap = StrokeCap.Round))
    } else {
        scale(1f, blink, pivot = Offset(54f, 62f)) { drawEyes() }
    }

    when (mood) {
        Mood.HAPPY -> drawPath(SMILE, Mouth, style = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))   // :3
        Mood.LOW -> drawPath(WOBBLY, Mouth, style = Stroke(1.1f, cap = StrokeCap.Round))
        Mood.HIGH -> drawOval(Mouth, Offset(52.7f, 70.4f), Size(2.6f, 3.2f))
        Mood.SLEEPY -> drawOval(Mouth, Offset(53.1f, 71.1f), Size(1.8f, 1.8f))
    }

    drawPath(SIDE_LOCKS, Hair)
    drawPath(SIDE_LOCKS, HairLine, style = Stroke(0.8f, join = StrokeJoin.Round))
    drawPath(BANGS, Hair)
    drawPath(BANGS, HairLine, style = Stroke(1f, join = StrokeJoin.Round))
    drawPath(HAIR_SHINE, Color.White.copy(alpha = 0.7f), style = Stroke(2f, cap = StrokeCap.Round))

    // the big bow wiggles along with the wings
    rotate(sway * 0.5f, pivot = Offset(62f, 25f)) {
        drawPath(BOW_LEFT, Bow)
        drawPath(BOW_RIGHT, Bow)
        drawPath(BOW_LEFT, Color(0xFF1C4FA3), style = Stroke(1f, join = StrokeJoin.Round))
        drawPath(BOW_RIGHT, Color(0xFF1C4FA3), style = Stroke(1f, join = StrokeJoin.Round))
        drawPath(BOW_FOLDS, Color(0xFFA9D3FF).copy(alpha = 0.8f), style = Stroke(0.9f, cap = StrokeCap.Round))
        drawPath(BOW_KNOT, Color(0xFF2A6FD6))
        drawPath(BOW_KNOT, Color(0xFF1C4FA3), style = Stroke(0.9f, join = StrokeJoin.Round))
    }

    // drawn over the bangs, as anime brows usually are
    when (mood) {
        Mood.LOW -> drawPath(WORRIED_BROWS, Ink, style = Stroke(1.1f, cap = StrokeCap.Round))
        Mood.HIGH -> drawPath(SWEAT, Color(0xFF9ED2FF))
        Mood.SLEEPY -> drawPath(SNOOZE, Color(0xFF6F8FEA), style = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        Mood.HAPPY -> Unit
    }
}

private fun DrawScope.drawEyes() {
    drawOval(Iris, Offset(40.6f, 56.5f), Size(9.4f, 11.8f))
    drawOval(Iris, Offset(58f, 56.5f), Size(9.4f, 11.8f))
    drawOval(Color(0xFF13285E), Offset(42.8f, 60.5f), Size(5f, 6.2f))
    drawOval(Color(0xFF13285E), Offset(60.2f, 60.5f), Size(5f, 6.2f))
    drawCircle(Color.White, 1.9f, Offset(43.5f, 59.9f))
    drawCircle(Color.White, 1.9f, Offset(60.9f, 59.9f))
    drawCircle(Color.White, 0.9f, Offset(47.4f, 65.6f))
    drawCircle(Color.White, 0.9f, Offset(64.8f, 65.6f))
    drawPath(LASHES, Ink, style = Stroke(1.9f, cap = StrokeCap.Round))
}

private fun DrawScope.drawWing(wing: Path, shine: Path, glint: Float) {
    drawPath(wing, Ice, alpha = 0.92f)
    drawPath(wing, Color(0xFF56B2EA), style = Stroke(0.9f, join = StrokeJoin.Round))
    drawPath(shine, Color.White.copy(alpha = glint), style = Stroke(0.9f, cap = StrokeCap.Round))
}
