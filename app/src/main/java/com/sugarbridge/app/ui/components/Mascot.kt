package com.sugarbridge.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tenshi-chan's mood: follows the glucose level, or "sleepy" while there is no data yet. */
enum class Mood { HAPPY, LOW, HIGH, SLEEPY }

// Shapes in the 108×108 launcher icon grid, the same as in ic_launcher_foreground.xml
private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val WING_LEFT = path("M40,73C34,63 25,52 15,47C16,52 18,55 21,57C16,58 16,62 20,64C17,66 18,70 22,71C21,75 25,78 30,77C31,81 36,82 41,79Z")
private val WING_RIGHT = path("M68,73C74,63 83,52 93,47C92,52 90,55 87,57C92,58 92,62 88,64C91,66 90,70 86,71C87,75 83,78 78,77C77,81 72,82 67,79Z")
private val FEATHERS_LEFT = path("M37,70C31,65 26,61 21,58M37,74C32,70 26,67 21,65M38,77C34,75 29,73 25,72")
private val FEATHERS_RIGHT = path("M71,70C77,65 82,61 87,58M71,74C76,70 82,67 87,65M70,77C74,75 79,73 83,72")
private val BACK_HAIR = path("M54,29C38,29 30,40 30,55C30,66 30,74 27,81C33,81 36,79 38,75L70,75C72,79 75,81 81,81C78,74 78,66 78,55C78,40 70,29 54,29Z")
private val DRESS = path("M36,97C35,89 42,82 50,81L58,81C66,82 73,89 72,97Q54,101 36,97Z")
private val RIBBON = path("M54,82.3C50,79.3 46,80.3 47,83.3C48,86.3 51,85.3 54,82.3ZM54,82.3C58,79.3 62,80.3 61,83.3C60,86.3 57,85.3 54,82.3Z")
private val RIBBON_TAILS = path("M53.4,83.3L51,89.8L53.2,88.8L54,90.8L54.8,88.8L57,89.8L54.6,83.3Z")
private val FACE = path("M36,54C36,44 44,40 54,40C64,40 72,44 72,54C72,64 68,72 62,76C59,78 56,79 54,79C52,79 49,78 46,76C40,72 36,64 36,54Z")
private val BLUSH_LINES = path("M38.4,67.4L37.6,69.2M40.4,67.4L39.6,69.2M42.4,67.4L41.6,69.2M66.4,67.4L65.6,69.2M68.4,67.4L67.6,69.2M70.4,67.4L69.6,69.2")
private val LASHES = path("M40.3,60.4Q41,56.4 45.3,56.3Q49.4,56.4 50.4,59.8M57.6,59.8Q58.6,56.4 62.7,56.3Q67,56.4 67.7,60.4M40.5,59.6L39,58.6M67.5,59.6L69,58.6")
private val SIDE_LOCKS = path("M36,49C33,57 33,67 35,77C37,73 38.5,67 39,61C39,56 38,52 36,49ZM72,49C75,57 75,67 73,77C71,73 69.5,67 69,61C69,56 70,52 72,49Z")
private val BANGS = path("M33,60C31,42 40,30 54,30C68,30 77,42 75,60C74,55 72,50 68.5,47.5C68.5,51 67,53.5 64,55C63,51 61.5,47.5 58.5,46C58,49.5 56.5,52 54,53.5C51.5,52 50,49.5 49.5,46C46.5,47.5 45,51 44,55C41,53.5 39.5,51 39.5,47.5C36,50 34,55 33,60Z")
private val HAIR_SHINE = path("M41,37Q54,31.5 67,37")
private val AHOGE = path("M53,31C53,25 58,21.5 63,23C58.5,23.5 56.5,26.5 56,31Z")
private val STAR_CLIP = path("M71,37.5L72.1,39.9L74.6,40.1L72.7,41.7L73.3,44.2L71,42.9L68.7,44.2L69.3,41.7L67.4,40.1L69.9,39.9Z")

// Mood details
private val SMILE = path("M51.6,71Q52.8,72.6 54,71.3Q55.2,72.6 56.4,71")
private val WOBBLY = path("M51.4,72.4Q52.7,71.1 54,72.2Q55.3,71.1 56.6,72.4")
private val WORRIED_BROWS = path("M41.5,54.2Q44.5,53.6 48.2,51.8M66.5,54.2Q63.5,53.6 59.8,51.8")
private val CLOSED_EYES = path("M40.6,61.2Q45.3,65.6 50,61.2M58,61.2Q62.7,65.6 67.4,61.2M40.8,61.4L39.2,60.4M67.2,61.4L68.8,60.4")
private val SNOOZE = path("M78,33L82,33L78,37L82,37M84,26L87,26L84,29L87,29")
private val SWEAT = path("M79,41C79,41 82.5,45.5 82.5,47.5A3.5,3.5 0,1 1,75.5 47.5C75.5,45.5 79,41 79,41Z")

// The happy face after a tap: eyes closed in arcs ^ ^ and a wide open smile
private val HAPPY_EYES = path("M40.8,62.6Q45.3,56.6 49.8,62.6M58.2,62.6Q62.7,56.6 67.2,62.6")
private val BIG_SMILE = path("M50.4,70.2Q54,76.8 57.6,70.2Q54,71.6 50.4,70.2Z")
private val HEART = path("M0,1.6C-2.8,-0.2 -1.6,-2.4 0,-0.8C1.6,-2.4 2.8,-0.2 0,1.6Z")

private val Ink = Color(0xFF3B2A4F)
private val Mouth = Color(0xFFB24C6B)
private val HairTop = Color(0xFFB8DEFF)
private val HairBottom = Color(0xFF7FAFF6)
private val HairLine = Color(0xFF6E9FE6)
private val Hair = Brush.verticalGradient(listOf(HairTop, HairBottom), startY = 28f, endY = 82f)
private val Iris = Brush.verticalGradient(
    0f to Color(0xFF4B56C9), 0.55f to Color(0xFF5E8EEA), 1f to Color(0xFFA6E0FF),
    startY = 56f, endY = 68f,
)

/** The neck: the head tilts around it. */
private val NECK = Offset(54f, 77f)

/**
 * Tenshi-chan. A tap makes her tilt her head to the side, close her eyes and smile, the way anime girls do,
 * then she straightens up again.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val motion = rememberInfiniteTransition(label = "mascot")
    val bob by motion.animateFloat(
        initialValue = -1.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val flap by motion.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(if (mood == Mood.SLEEPY) 2600 else 900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flap",
    )
    val glow by motion.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow",
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

    val joy = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var poke by remember { mutableStateOf<Job?>(null) }
    val tap = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        poke?.cancel()
        poke = scope.launch {
            joy.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f))
            delay(1200)
            joy.animateTo(0f, tween(450))
        }
    }

    Canvas(modifier.size(size).graphicsLayer().then(tap)) {   // own layer: the animation does not redraw the card
        val k = this.size.minDimension / 108f
        scale(k, k, pivot = Offset.Zero) {
            translate(top = bob) { drawAngel(mood, flap, glow, blink, joy.value) }
        }
    }
}

private fun DrawScope.drawAngel(mood: Mood, flap: Float, glow: Float, blink: Float, joy: Float) {
    val tilt = -13f * joy   // the head leans to her right
    val happy = joy > 0.35f

    // wings flap around the shoulders
    rotate(-flap, pivot = Offset(40f, 74f)) { drawWing(WING_LEFT, FEATHERS_LEFT) }
    rotate(flap, pivot = Offset(68f, 74f)) { drawWing(WING_RIGHT, FEATHERS_RIGHT) }

    // the back of the head first, so the dress covers the hair ends
    rotate(tilt, pivot = NECK) {
        // halo with a pulsing glow
        val halo = Size(26f, 7.6f)
        val haloAt = Offset(41f, 19.2f)
        drawOval(Color(0xFFFFE7A3).copy(alpha = glow * 0.6f), haloAt, halo, style = Stroke(5f))
        drawOval(Color(0xFFFFC23D), haloAt, halo, style = Stroke(2.6f))
        drawOval(Color(0xFFFFF4CC), haloAt, halo, style = Stroke(0.8f))
        drawPath(BACK_HAIR, Hair)
        drawPath(BACK_HAIR, HairLine, style = Stroke(1f))
    }

    // neck, dress and the red ribbon
    drawRect(Color(0xFFF7D3C4), Offset(50f, 74f), Size(8f, 8f))
    drawPath(DRESS, Color.White)
    drawPath(DRESS, Color(0xFFC9B5FF), style = Stroke(1f))
    drawPath(RIBBON_TAILS, Color(0xFFC62828))
    drawPath(RIBBON, Color(0xFFE53935))
    drawPath(RIBBON, Color(0xFFB71C1C), style = Stroke(0.5f))
    drawCircle(Color(0xFFB71C1C), 1.6f, Offset(54f, 82.4f))

    rotate(tilt, pivot = NECK) {
        // face: pale when low, flushed when high
        val skin = when {
            happy -> Color(0xFFFFEBE0)
            mood == Mood.LOW -> Color(0xFFF6EEF4)
            mood == Mood.HIGH -> Color(0xFFFFE1D8)
            else -> Color(0xFFFFEBE0)
        }
        drawPath(FACE, skin)
        drawPath(FACE, Color(0xFFE9B4A0), style = Stroke(0.9f))

        val blush = when {
            happy -> 0.85f
            mood == Mood.HIGH -> 0.85f
            mood == Mood.LOW -> 0.25f
            else -> 0.55f
        }
        drawOval(Color(0xFFFF8FB8).copy(alpha = blush), Offset(36.4f, 66.6f), Size(7.2f, 3.8f))
        drawOval(Color(0xFFFF8FB8).copy(alpha = blush), Offset(64.4f, 66.6f), Size(7.2f, 3.8f))
        drawPath(BLUSH_LINES, Color(0xFFFF7FAE).copy(alpha = 0.8f), style = Stroke(0.6f, cap = StrokeCap.Round))

        when {
            happy -> drawPath(HAPPY_EYES, Ink, style = Stroke(2f, cap = StrokeCap.Round))
            mood == Mood.SLEEPY -> drawPath(CLOSED_EYES, Ink, style = Stroke(1.6f, cap = StrokeCap.Round))
            else -> scale(1f, blink, pivot = Offset(54f, 62f)) { drawEyes() }
        }

        when {
            happy -> drawPath(BIG_SMILE, Mouth)
            mood == Mood.HAPPY -> drawPath(SMILE, Mouth, style = Stroke(1.1f, cap = StrokeCap.Round))
            mood == Mood.LOW -> drawPath(WOBBLY, Mouth, style = Stroke(1.1f, cap = StrokeCap.Round))
            mood == Mood.HIGH -> drawOval(Mouth, Offset(52.7f, 70.3f), Size(2.6f, 3.2f))
            else -> drawOval(Mouth, Offset(53.1f, 71f), Size(1.8f, 1.8f))
        }

        drawPath(SIDE_LOCKS, Hair)
        drawPath(SIDE_LOCKS, HairLine, style = Stroke(0.8f))
        drawPath(BANGS, Hair)
        drawPath(BANGS, HairLine, style = Stroke(1f, join = StrokeJoin.Round))
        drawPath(HAIR_SHINE, Color.White.copy(alpha = 0.65f), style = Stroke(2f, cap = StrokeCap.Round))
        drawPath(AHOGE, Hair)
        drawPath(AHOGE, HairLine, style = Stroke(0.8f))
        drawPath(STAR_CLIP, Color(0xFFFFD25E))
        drawPath(STAR_CLIP, Color(0xFFF0B43C), style = Stroke(0.5f, join = StrokeJoin.Round))

        // drawn over the bangs, as anime brows usually are
        if (!happy) {
            when (mood) {
                Mood.LOW -> drawPath(WORRIED_BROWS, Ink, style = Stroke(1.1f, cap = StrokeCap.Round))
                Mood.HIGH -> drawPath(SWEAT, Color(0xFF9ED2FF))
                Mood.SLEEPY -> drawPath(SNOOZE, Color(0xFF9C8CF0), style = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                Mood.HAPPY -> Unit
            }
        }
    }

    // two little hearts float up while she smiles
    if (joy > 0.05f) {
        val heart = Color(0xFFFF7FAE).copy(alpha = joy.coerceAtMost(1f))
        translate(left = 82f, top = 40f - 10f * joy) { scale(1.6f, pivot = Offset.Zero) { drawPath(HEART, heart) } }
        translate(left = 25f, top = 46f - 7f * joy) { scale(1.2f, pivot = Offset.Zero) { drawPath(HEART, heart) } }
    }
}

private fun DrawScope.drawEyes() {
    drawOval(Iris, Offset(40.7f, 56.6f), Size(9.2f, 11.4f))
    drawOval(Iris, Offset(58.1f, 56.6f), Size(9.2f, 11.4f))
    drawOval(Color(0xFF28307A), Offset(42.9f, 60.4f), Size(4.8f, 6f))
    drawOval(Color(0xFF28307A), Offset(60.3f, 60.4f), Size(4.8f, 6f))
    drawCircle(Color.White, 1.9f, Offset(43.6f, 59.9f))
    drawCircle(Color.White, 1.9f, Offset(61f, 59.9f))
    drawCircle(Color.White, 0.9f, Offset(47.3f, 65.3f))
    drawCircle(Color.White, 0.9f, Offset(64.7f, 65.3f))
    drawPath(LASHES, Ink, style = Stroke(1.9f, cap = StrokeCap.Round))
}

private fun DrawScope.drawWing(wing: Path, feathers: Path) {
    drawPath(wing, Color.White)
    drawPath(wing, Color(0xFFC3ABFF), style = Stroke(1.1f, join = StrokeJoin.Round))
    drawPath(feathers, Color(0xFFDCCFFF), style = Stroke(0.8f, cap = StrokeCap.Round))
}
