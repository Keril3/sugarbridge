package com.sugarbridge.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.R
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------------------
// The sky
// ---------------------------------------------------------------------------------------------------------

/** The background of the whole app, as on keril.art: a sky gradient, a field of dots and floating bubbles, stars and hearts. */
@Composable
fun SkyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val xp = LocalXp.current
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(xp.sky))) {
        DotField()
        Floaters()
        // a 10% shade keeps the background from competing with the windows, like on the site
        Box(Modifier.fillMaxSize().background(Color(0x1A0B2545)))
        CompositionLocalProvider(LocalContentColor provides xp.text) {
            content()
        }
    }
}

// The dot tiles of keril.art: x, y, radius and opacity in a 96 and a 131 unit tile
private val TILE_A = listOf(
    floatArrayOf(14f, 18f, 1.4f, .75f), floatArrayOf(47f, 9f, 1f, .5f), floatArrayOf(79f, 21f, 1.6f, .8f),
    floatArrayOf(28f, 46f, 1f, .55f), floatArrayOf(62f, 41f, 1.3f, .7f), floatArrayOf(90f, 55f, .9f, .45f),
    floatArrayOf(9f, 77f, 1.1f, .6f), floatArrayOf(44f, 82f, 1.5f, .75f), floatArrayOf(74f, 73f, 1f, .5f),
)
private val TILE_B = listOf(
    floatArrayOf(31f, 64f, 1.2f, .6f), floatArrayOf(97f, 29f, .9f, .5f), floatArrayOf(112f, 103f, 1.3f, .65f), floatArrayOf(58f, 120f, .9f, .45f),
)

@Composable
private fun DotField() {
    // a separate layer that never animates: the dots are drawn once per size change
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        listOf(96f to TILE_A, 131f to TILE_B).forEach { (tile, dots) ->
            val step = tile.dp.toPx()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    dots.forEach { d ->
                        drawCircle(Color.White.copy(alpha = d[3]), d[2].dp.toPx(), Offset(x + d[0].dp.toPx(), y + d[1].dp.toPx()))
                    }
                    x += step
                }
                y += step
            }
        }
    }
}

private enum class FloaterKind { BUBBLE, STAR, HEART }

private class Floater(val kind: FloaterKind, val x: Float, val phase: Float, val speed: Float, val size: Float, val white: Boolean)

@Composable
private fun Floaters() {
    val xp = LocalXp.current
    val night = LocalNight.current
    val floaters = remember {
        val random = Random(11)
        List(24) {
            val kind = when (it % 8) {
                0, 3, 5 -> FloaterKind.BUBBLE
                1, 4, 6 -> FloaterKind.STAR
                else -> FloaterKind.HEART
            }
            Floater(kind, random.nextFloat(), random.nextFloat(), 0.4f + random.nextFloat() * 0.6f, random.nextFloat(), random.nextBoolean())
        }
    }
    val time by rememberInfiniteTransition(label = "sky").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "time",
    )
    // own layer: the floaters move without redrawing the screen above them
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val w = size.width
        val h = size.height
        floaters.forEach { f ->
            when (f.kind) {
                FloaterKind.BUBBLE -> {
                    val r = (5 + f.size * 8).dp.toPx()
                    val y = (f.phase - time * f.speed * 2).mod(1f) * (h + 4 * r) - 2 * r
                    val x = w * f.x + sin(2 * PI * (time * 3 + f.phase)).toFloat() * 10.dp.toPx()
                    drawCircle(Color.White.copy(alpha = if (night) 0.10f else 0.16f), r, Offset(x, y))
                    drawCircle(Color.White.copy(alpha = if (night) 0.45f else 0.7f), r, Offset(x, y), style = Stroke(1.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
                }
                FloaterKind.STAR -> {
                    val glow = 0.5f + 0.5f * sin(2 * PI * (time * 12 + f.phase)).toFloat()
                    val r = (4 + f.size * 6).dp.toPx() * (0.85f + 0.25f * glow)
                    val color = if (f.white) Color.White else xp.floaterStar
                    sparkle(Offset(w * f.x, h * f.phase), r, color.copy(alpha = 0.35f + 0.6f * glow))
                }
                FloaterKind.HEART -> {
                    val s = (3.5f + f.size * 3.5f).dp.toPx()
                    val y = (f.phase - time * f.speed).mod(1f) * (h + 4 * s) - 2 * s
                    val x = w * f.x + sin(2 * PI * (time * 2 + f.phase)).toFloat() * 14.dp.toPx()
                    heart(Offset(x, y), s, Tenshi.Pink.copy(alpha = 0.9f))
                }
            }
        }
    }
}

/** A four-pointed star with concave sides ✦. */
fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val (x, y) = center
    val path = Path().apply {
        moveTo(x, y - radius)
        quadraticTo(x, y, x + radius, y)
        quadraticTo(x, y, x, y + radius)
        quadraticTo(x, y, x - radius, y)
        quadraticTo(x, y, x, y - radius)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.heart(center: Offset, s: Float, color: Color) {
    val (x, y) = center
    val path = Path().apply {
        moveTo(x, y + s * 0.9f)
        cubicTo(x - s * 1.6f, y - s * 0.1f, x - s * 0.9f, y - s * 1.3f, x, y - s * 0.45f)
        cubicTo(x + s * 0.9f, y - s * 1.3f, x + s * 1.6f, y - s * 0.1f, x, y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}

// ---------------------------------------------------------------------------------------------------------
// Windows
// ---------------------------------------------------------------------------------------------------------

private val WindowShape = RoundedCornerShape(10.dp)

/**
 * A Windows XP style window, as on keril.art: a gradient title bar with an outlined title and three buttons.
 * "_" rolls the window up into its title bar and back; "×" closes it when [onClose] is given,
 * otherwise the window just bounces.
 */
@Composable
fun XpWindow(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    bodyPadding: PaddingValues = PaddingValues(16.dp),
    onBodyClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val xp = LocalXp.current
    var collapsed by rememberSaveable(title) { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxWidth()
            .scale(bounce.value)
            .shadow(10.dp, WindowShape, ambientColor = xp.shadow, spotColor = xp.shadow)
            .clip(WindowShape)
            .border(2.dp, xp.windowBorder, WindowShape)
            .background(xp.windowBody),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(xp.bar))
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTitle(title, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WindowButton(
                    if (collapsed) WindowGlyph.RESTORE else WindowGlyph.MINIMIZE,
                    stringResource(if (collapsed) R.string.window_expand else R.string.window_collapse),
                ) { collapsed = !collapsed }
                WindowButton(WindowGlyph.MAXIMIZE, stringResource(R.string.window_expand)) { collapsed = false }
                WindowButton(WindowGlyph.CLOSE, stringResource(R.string.close)) {
                    if (onClose != null) {
                        onClose()
                    } else {
                        scope.launch {
                            bounce.animateTo(1.04f, tween(110))
                            bounce.animateTo(1f, tween(220))
                        }
                    }
                }
            }
        }
        AnimatedVisibility(!collapsed) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (onBodyClick != null) Modifier.clickable(onClick = onBodyClick) else Modifier)
                    .padding(bodyPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** White text with a black outline and a soft shadow, like the window titles on keril.art. */
@Composable
fun OutlinedTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    val stroke = with(LocalDensity.current) { 2.2.dp.toPx() }
    Box(modifier) {
        Text(
            text,
            style = style.copy(drawStyle = Stroke(stroke, join = StrokeJoin.Round)),
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},   // only the outline: screen readers read the text once
        )
        Text(
            text,
            style = style.copy(shadow = Shadow(Color(0x59000000), Offset(0f, 2f), 2f)),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class WindowGlyph { MINIMIZE, RESTORE, MAXIMIZE, CLOSE }

@Composable
private fun WindowButton(glyph: WindowGlyph, description: String, onClick: () -> Unit) {
    val close = glyph == WindowGlyph.CLOSE
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(width = 22.dp, height = 20.dp)
            .clip(shape)
            .border(1.dp, if (close) Tenshi.DangerBorder else Tenshi.SmallButtonBorder, shape)
            .background(Brush.verticalGradient(if (close) Tenshi.CloseGloss else Tenshi.SmallButtonGloss))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) {
            val c = if (close) Color.White else Tenshi.ButtonText
            val s = size.minDimension
            val w = 1.6.dp.toPx()
            when (glyph) {
                WindowGlyph.MINIMIZE -> drawLine(c, Offset(s * 0.1f, s * 0.85f), Offset(s * 0.9f, s * 0.85f), w * 1.3f)
                WindowGlyph.RESTORE -> {
                    drawRect(c, Offset(s * 0.1f, s * 0.35f), Size(s * 0.55f, s * 0.55f), style = Stroke(w))
                    drawLine(c, Offset(s * 0.35f, s * 0.12f), Offset(s * 0.9f, s * 0.12f), w)
                    drawLine(c, Offset(s * 0.9f, s * 0.12f), Offset(s * 0.9f, s * 0.65f), w)
                }
                WindowGlyph.MAXIMIZE -> {
                    drawRect(c, Offset(s * 0.1f, s * 0.1f), Size(s * 0.8f, s * 0.8f), style = Stroke(w))
                    drawLine(c, Offset(s * 0.1f, s * 0.2f), Offset(s * 0.9f, s * 0.2f), w * 1.4f)
                }
                WindowGlyph.CLOSE -> {
                    drawLine(c, Offset(s * 0.15f, s * 0.15f), Offset(s * 0.85f, s * 0.85f), w * 1.2f, cap = StrokeCap.Round)
                    drawLine(c, Offset(s * 0.85f, s * 0.15f), Offset(s * 0.15f, s * 0.85f), w * 1.2f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Buttons and controls
// ---------------------------------------------------------------------------------------------------------

private val ButtonShape = RoundedCornerShape(6.dp)

private fun gloss(stops: List<Color>): Brush =
    if (stops.size == 4) Brush.verticalGradient(0f to stops[0], 0.48f to stops[1], 0.5f to stops[2], 1f to stops[3])
    else Brush.verticalGradient(stops)

@Composable
private fun XpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    border: Color,
    stops: List<Color>,
    textColor: Color,
    minHeight: Dp = 44.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    Box(
        modifier
            .heightIn(min = minHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .offset { IntOffset(0, if (pressed) 1.dp.roundToPx() else 0) }
            .shadow(if (active && !pressed) 3.dp else 0.dp, ButtonShape, spotColor = Color(0x5514468C))
            .clip(ButtonShape)
            .border(1.dp, border, ButtonShape)
            .background(gloss(stops))
            .then(if (pressed) Modifier.background(Color(0x1A000000)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Tenshi.ButtonText, strokeWidth = 2.dp)
        } else {
            Text(text, color = textColor, style = textStyle, textAlign = TextAlign.Center)
        }
    }
}

/** The main glossy blue button. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) =
    XpButton(text, onClick, modifier, enabled, loading, Tenshi.ButtonBorder, Tenshi.ButtonGloss, Tenshi.ButtonText)

/** A light secondary button; [danger] makes it red for deleting and signing out. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) =
    XpButton(
        text, onClick, modifier, enabled, false,
        if (danger) Tenshi.DangerBorder else Tenshi.SecondaryBorder,
        if (danger) Tenshi.DangerGloss else Tenshi.SecondaryGloss,
        if (danger) Tenshi.DangerText else Tenshi.ButtonText,
    )

/** Buttons in a row; whatever doesn't fit wraps to the next line as a whole. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** A row of toggle buttons, one of them pressed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val on = option == selected
            XpButton(
                label(option), { onSelect(option) }, Modifier, enabled, false,
                if (on) Tenshi.ButtonBorder else Tenshi.SecondaryBorder,
                if (on) Tenshi.ButtonGloss else Tenshi.SecondaryGloss,
                Tenshi.ButtonText,
                minHeight = 36.dp,
                textStyle = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** A settings row with a switch. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val xp = LocalXp.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = xp.textMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF3C82D6),
                checkedThumbColor = Color.White,
                checkedBorderColor = Tenshi.ButtonBorder,
                uncheckedTrackColor = xp.windowBody,
                uncheckedBorderColor = Color(0xFF8EBDE8),
                uncheckedThumbColor = Color(0xFF8EBDE8),
            ),
        )
    }
}

/** A colored badge with a label, e.g. "in range ♡". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier.clip(shape).border(1.dp, color, shape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** The trend arrow, drawn rather than a character: 1 = down … 3 = right … 5 = up. */
@Composable
fun TrendArrow(trend: Int, color: Color, size: Dp = 40.dp) {
    if (trend !in 1..5) return
    val angle = (3 - trend) * 45f   // 3 is horizontal, each step is 45°
    Canvas(Modifier.size(size).rotate(angle)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(s * 0.18f, s / 2), Offset(s * 0.80f, s / 2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(s * 0.56f, s * 0.26f)
            lineTo(s * 0.82f, s / 2)
            lineTo(s * 0.56f, s * 0.74f)
        }
        drawPath(head, color, style = stroke)
    }
}

/** A dotted line between rows, like the lists on keril.art. */
@Composable
fun DottedDivider(modifier: Modifier = Modifier) {
    val color = LocalXp.current.dots
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx()))
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(), pathEffect = dash)
    }
}

/** An XP progress bar: glossy blocks in a rounded trough. */
@Composable
fun XpMeter(fraction: Float, modifier: Modifier = Modifier, color: Color = Color(0xFF4FA8E8)) {
    val night = LocalNight.current
    val shape = RoundedCornerShape(6.dp)
    Canvas(
        modifier
            .height(12.dp)
            .clip(shape)
            .border(1.dp, if (night) Color(0xFF6F8FEA) else Tenshi.ButtonBorder, shape)
            .background(if (night) Color(0xCC050A24) else Color(0xFFEAF6FF)),
    ) {
        val inset = 2.dp.toPx()
        val block = 7.dp.toPx()
        val gap = 2.dp.toPx()
        val end = inset + (size.width - 2 * inset) * fraction.coerceIn(0f, 1f)
        var x = inset
        while (x < end) {
            val w = minOf(block, end - x)
            drawRect(color, Offset(x, inset), Size(w, size.height - 2 * inset))
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), startY = inset, endY = size.height * 0.6f),
                Offset(x, inset), Size(w, size.height - 2 * inset),
            )
            x += block + gap
        }
    }
}

/** A caption in small muted text. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = LocalXp.current.textMuted)
}

/** Bottom space so the last item doesn't hide under the tab bar. */
@Composable
fun BottomSpace() = Spacer(Modifier.height(24.dp))

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------------------
// The address bar
// ---------------------------------------------------------------------------------------------------------

/** A language for the language menu: its tag (null = the phone's) and its name. */
class LanguageItem(val tag: String?, val name: String)

/**
 * The address bar from the top of keril.art: "C:\SugarBridge\now_" with a blinking cursor, the connection
 * state, the light/dark switch and the language menu.
 */
@Composable
fun AddressBar(
    path: String,
    online: Boolean,
    onToggleTheme: () -> Unit,
    languageLabel: String,
    languages: List<LanguageItem>,
    currentLanguage: String?,
    onLanguage: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val xp = LocalXp.current
    val night = LocalNight.current
    val cursor by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1000
                1f at 490
                0f at 500
                0f at 990
            },
        ),
        label = "blink",
    )
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, CircleShape, spotColor = xp.shadow)
            .clip(CircleShape)
            .border(1.dp, xp.statusBorder, CircleShape)
            .background(Brush.verticalGradient(xp.statusBar))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f)) {
            Text(
                "C:\\SugarBridge\\$path",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("_", style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(cursor))
        }
        Spacer(Modifier.width(8.dp))
        Text(if (online) "online" else "offline", style = MaterialTheme.typography.labelMedium, color = xp.textMuted)
        Spacer(Modifier.width(8.dp))
        SmallGlossButton(stringResource(R.string.theme_toggle), onToggleTheme) {
            Canvas(Modifier.size(14.dp)) { if (night) moon() else sun() }
        }
        Spacer(Modifier.width(4.dp))
        Box {
            SmallGlossButton(stringResource(R.string.language_card), { menu = true }, minWidth = 46.dp) {
                Text("$languageLabel ▾", style = MaterialTheme.typography.labelMedium, color = Tenshi.ButtonText)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = xp.windowBody,
                shape = RoundedCornerShape(8.dp),
            ) {
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.name, style = MaterialTheme.typography.bodyMedium, color = xp.text) },
                        onClick = {
                            menu = false
                            onLanguage(language.tag)
                        },
                        trailingIcon = if (language.tag == currentLanguage) {
                            { Text("✓", color = xp.accent) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallGlossButton(description: String, onClick: () -> Unit, minWidth: Dp = 30.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .height(28.dp)
            .widthIn(min = minWidth)
            .clip(shape)
            .border(1.dp, Tenshi.ButtonBorder, shape)
            .background(gloss(Tenshi.ButtonGloss))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun DrawScope.sun() {
    val c = Tenshi.ButtonText
    val center = Offset(size.width / 2, size.height / 2)
    val s = size.minDimension
    drawCircle(c, s * 0.2f, center, style = Stroke(s * 0.1f))
    for (i in 0 until 8) {
        val a = i * PI / 4
        val from = Offset(center.x + (s * 0.32f) * cos(a).toFloat(), center.y + (s * 0.32f) * sin(a).toFloat())
        val to = Offset(center.x + (s * 0.46f) * cos(a).toFloat(), center.y + (s * 0.46f) * sin(a).toFloat())
        drawLine(c, from, to, s * 0.1f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.moon() {
    val s = size.minDimension
    val path = Path().apply {
        moveTo(s * 0.62f, s * 0.08f)
        cubicTo(s * 0.18f, s * 0.12f, s * 0.05f, s * 0.7f, s * 0.42f, s * 0.9f)
        cubicTo(s * 0.62f, s * 1.0f, s * 0.85f, s * 0.9f, s * 0.95f, s * 0.7f)
        cubicTo(s * 0.6f, s * 0.75f, s * 0.38f, s * 0.4f, s * 0.62f, s * 0.08f)
        close()
    }
    drawPath(path, Tenshi.ButtonText)
}
