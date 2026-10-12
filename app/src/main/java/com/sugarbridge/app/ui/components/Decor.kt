package com.sugarbridge.app.ui.components

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.trendAngle
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.ProvideTextScale
import com.sugarbridge.app.ui.theme.Tenshi
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------------------
// The sky
// ---------------------------------------------------------------------------------------------------------

/** The background of the whole app, as on keril.art: a sky gradient and a still field of dots under a tint. */
@Composable
fun SkyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val xp = LocalXp.current
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(xp.sky))) {
        if (xp.starry) {
            DotField()
            // a 20% tint: the shade of the site plus 10% more, so text on the sky reads well
            Box(Modifier.fillMaxSize().background(Color(0x330B2545)))
        }
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

// ---------------------------------------------------------------------------------------------------------
// Windows
// ---------------------------------------------------------------------------------------------------------

/** A dialog with the app's text size: a dialog window gets the phone's font size again. */
@Composable
fun XpDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {
    Dialog(onDismissRequest, properties) { ProvideTextScale(content) }
}

private val WindowShape = RoundedCornerShape(10.dp)

/** Colors of a window that stands out from the others, such as the pink message windows. */
class WindowColors(val bar: List<Color>, val border: Color, val body: Color)

/** The pink of the message windows: a pink frame and title bar, a pale pink (by night, plum) body. */
@Composable
fun pinkWindow(): WindowColors =
    if (LocalNight.current) WindowColors(listOf(Color(0xFFD45A9A), Color(0xFF8E2B66)), Color(0xFFFF8FC8), Color(0xFF2B1230))
    else WindowColors(listOf(Color(0xFFFFD3EA), Color(0xFFEE6FA8)), Color(0xFFE0559A), Color(0xFFFFF2F8))

/**
 * A Windows XP style window, as on keril.art: a gradient title bar with an outlined title above the content.
 * The content starts a little further from the title than its lines are from each other. [colors] dress it
 * differently from the theme's windows.
 */
@Composable
fun XpWindow(
    title: String,
    modifier: Modifier = Modifier,
    bodyPadding: PaddingValues = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
    onBodyClick: (() -> Unit)? = null,
    framed: Boolean = true,
    colors: WindowColors? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val xp = LocalXp.current
    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, WindowShape, ambientColor = xp.shadow, spotColor = xp.shadow)
            .clip(WindowShape)
            .then(if (framed) Modifier.border(2.dp, colors?.border ?: xp.windowBorder, WindowShape) else Modifier)
            .background(colors?.body ?: xp.windowBody),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(colors?.bar ?: xp.bar))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTitle(title, Modifier.weight(1f))
            if (onClose != null) CloseButton(onClose)
        }
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

/** The red ✕ of an XP window, for the few windows that can be closed. */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    val label = stringResource(R.string.close)
    Box(
        Modifier
            .size(26.dp)
            .clip(shape)
            .border(1.dp, Color.White, shape)
            .background(Brush.verticalGradient(Tenshi.CloseGloss))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClose)
            .semantics { contentDescription = label }
            .testTag("window-close"),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = Color.White, style = MaterialTheme.typography.labelLarge)
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
    icon: Int? = null,
    horizontalPadding: Dp = 16.dp,
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
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            XpMarquee(Modifier.width(96.dp))   // a request is being handled: blocks run through the bar
        } else if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(16.dp))
                Text(text, color = textColor, style = textStyle, textAlign = TextAlign.Center)
            }
        } else {
            Text(text, color = textColor, style = textStyle, textAlign = TextAlign.Center)
        }
    }
}

/** The main glossy blue button. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) =
    XpButton(text, onClick, modifier, enabled, loading, Tenshi.ButtonBorder, Tenshi.ButtonGloss, Tenshi.ButtonText)

/** A light secondary button; [danger] makes it red for deleting and signing out; [icon] stands before the text. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, icon: Int? = null) =
    XpButton(
        text, onClick, modifier, enabled, false,
        if (danger) Tenshi.DangerBorder else Tenshi.SecondaryBorder,
        if (danger) Tenshi.DangerGloss else Tenshi.SecondaryGloss,
        if (danger) Tenshi.DangerText else Tenshi.ButtonText,
        icon = icon,
    )

/** A small light button for a row of quick choices, such as "All" and "None" over a list. */
@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    XpButton(
        text, onClick, modifier, enabled, false, Tenshi.SecondaryBorder, Tenshi.SecondaryGloss, Tenshi.ButtonText,
        minHeight = 34.dp, textStyle = MaterialTheme.typography.labelMedium, horizontalPadding = 10.dp,
    )

/**
 * A translucent bar like the tab bar, for a heading with its buttons that stands right on the sky: on the
 * sky's blue alone, text and blue links are hard to read.
 */
@Composable
fun GlassBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val xp = LocalXp.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .shadow(4.dp, shape, spotColor = xp.shadow)
            .clip(shape)
            .border(1.dp, xp.statusBorder, shape)
            .background(Brush.verticalGradient(xp.statusBar))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The small square glossy button with a chain that opens a link, as on keril.art; [label] is for screen readers. */
@Composable
fun LinkButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(width = 34.dp, height = 32.dp)
            .shadow(2.dp, shape, spotColor = Color(0x4014468C))
            .clip(shape)
            .border(1.dp, Tenshi.SmallButtonBorder, shape)
            .background(Brush.verticalGradient(0f to Color(0xFFF4FAFF), 0.48f to Color(0xFFCFE6FB), 0.52f to Color(0xFFA9D0F3), 1f to Color(0xFF7FB4E8)))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_link_chain), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
    }
}

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
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val xp = LocalXp.current
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    val angle = trendAngle(trend) ?: return
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

private val MeterBlock = 7.dp
private val MeterGap = 2.dp
private val MeterInset = 2.dp
private val MeterRadius = 6.dp
private const val METER_STEP_MS = 40L      // the slowest a new block follows the real progress: tick, tick, tick
internal const val METER_CATCH_UP_MS = 240L // however far behind, the bar shows the real progress within this
private const val MARQUEE_STEP_MS = 90L

/** How long a finished bar stays full before it goes, so the last blocks are seen. */
const val METER_FULL_HOLD_MS = 400L

/** How many whole blocks fit a bar [width] wide. */
internal fun meterCapacity(width: Float, block: Float, gap: Float, inset: Float): Int =
    if (width <= 2 * inset + block) 0 else ((width - 2 * inset + gap) / (block + gap)).toInt()

/** The blocks shown for [fraction]: whole blocks only, the nearest number, so 100% is a full bar and 0% an empty one. */
internal fun meterBlocks(fraction: Float, capacity: Int): Int =
    Math.round(fraction.coerceIn(0f, 1f) * capacity)

/** The trough of an XP bar: a rounded frame with a light (by night, dark) inside. */
@Composable
private fun Modifier.meterTrough(): Modifier {
    val night = LocalNight.current
    val shape = RoundedCornerShape(MeterRadius)
    return this
        .height(12.dp)
        .clip(shape)
        .border(1.dp, if (night) Color(0xFF6F8FEA) else Tenshi.ButtonBorder, shape)
        .background(if (night) Color(0xCC050A24) else Color(0xFFEAF6FF))
}

/**
 * How wide each of [capacity] blocks is so that, with their gaps, they fill the bar from end to end: the
 * blocks are a little wider than [MeterBlock] instead of leaving a strip of empty trough at the end.
 */
internal fun meterBlockWidth(width: Float, capacity: Int, gap: Float, inset: Float): Float =
    if (capacity <= 0) 0f else (width - 2 * inset - (capacity - 1) * gap) / capacity

/**
 * The blocks [indices] of a bar of [capacity], clipped to the inside of the rounded trough: the end blocks
 * follow its rounding instead of standing out over the frame.
 */
private fun DrawScope.meterBlocks(indices: Iterable<Int>, capacity: Int, color: Color) {
    val inset = MeterInset.toPx()
    val gap = MeterGap.toPx()
    val border = 1.dp.toPx()
    val blockWidth = meterBlockWidth(size.width, capacity, gap, inset)
    val inner = Path().apply {
        addRoundRect(RoundRect(border, border, size.width - border, size.height - border, CornerRadius(MeterRadius.toPx() - border)))
    }
    val block = Size(blockWidth, size.height - 2 * inset)
    val shine = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), startY = inset, endY = size.height * 0.6f)
    clipPath(inner) {
        for (i in indices) {
            if (i !in 0 until capacity) continue
            val at = Offset(inset + i * (blockWidth + gap), inset)
            drawRect(color, at, block)
            drawRect(shine, at, block)
        }
    }
}

/**
 * An XP progress bar: glossy blocks in a rounded trough, for real progress (a download, a page, writing
 * days). As in Windows XP it never shows part of a block. It shows the real value: the first one at once, and
 * when the value grows, the new blocks appear one after another but catch up within [METER_CATCH_UP_MS], so
 * the bar is never behind the work. A smaller value is shown at once. Work without a known share of the whole
 * gets [XpMarquee] instead.
 */
@Composable
fun XpMeter(fraction: Float, modifier: Modifier = Modifier, color: Color = Color(0xFF4FA8E8)) {
    val density = LocalDensity.current
    var capacity by remember { mutableIntStateOf(0) }
    val target = meterBlocks(fraction, capacity)
    var shown by remember { mutableIntStateOf(-1) }   // -1: nothing measured yet
    LaunchedEffect(target, capacity) {
        if (capacity == 0) return@LaunchedEffect
        if (shown < 0 || target < shown) {
            shown = target
            return@LaunchedEffect
        }
        val step = (METER_CATCH_UP_MS / (target - shown).coerceAtLeast(1)).coerceAtMost(METER_STEP_MS)
        while (shown < target) {
            delay(step)
            shown++
        }
    }
    Canvas(
        modifier
            .meterTrough()
            .onSizeChanged { capacity = with(density) { meterCapacity(it.width.toFloat(), MeterBlock.toPx(), MeterGap.toPx(), MeterInset.toPx()) } }
            // what the bar shows, for screen readers and tests
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(if (capacity > 0) shown.coerceIn(0, capacity).toFloat() / capacity else 0f, 0f..1f) },
    ) {
        meterBlocks(0 until shown.coerceAtMost(capacity), capacity, color)
    }
}

/**
 * An XP bar for work of unknown length: a group of three blocks runs through the trough block by block and
 * starts over, the way Windows XP showed it was busy.
 */
@Composable
fun XpMarquee(modifier: Modifier = Modifier, color: Color = Color(0xFF4FA8E8)) {
    val density = LocalDensity.current
    var capacity by remember { mutableIntStateOf(0) }
    var head by remember { mutableIntStateOf(0) }
    LaunchedEffect(capacity) {
        while (capacity > 0) {
            delay(MARQUEE_STEP_MS)
            head = (head + 1) % (capacity + 3)
        }
    }
    Canvas(
        modifier
            .meterTrough()
            .onSizeChanged { capacity = with(density) { meterCapacity(it.width.toFloat(), MeterBlock.toPx(), MeterGap.toPx(), MeterInset.toPx()) } }
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .testTag("xp-marquee"),
    ) {
        meterBlocks(head - 2..head, capacity, color)
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
