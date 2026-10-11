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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi

// ---------------------------------------------------------------------------------------------------------
// The sky
// ---------------------------------------------------------------------------------------------------------

/** The background of the whole app, as on keril.art: a sky gradient and a still field of dots. */
@Composable
fun SkyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val xp = LocalXp.current
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(xp.sky))) {
        DotField()
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

// ---------------------------------------------------------------------------------------------------------
// Windows
// ---------------------------------------------------------------------------------------------------------

private val WindowShape = RoundedCornerShape(10.dp)

/** A Windows XP style window, as on keril.art: a gradient title bar with an outlined title above the content. */
@Composable
fun XpWindow(
    title: String,
    modifier: Modifier = Modifier,
    bodyPadding: PaddingValues = PaddingValues(16.dp),
    onBodyClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val xp = LocalXp.current
    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, WindowShape, ambientColor = xp.shadow, spotColor = xp.shadow)
            .clip(WindowShape)
            .border(2.dp, xp.windowBorder, WindowShape)
            .background(xp.windowBody),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(xp.bar))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTitle(title, Modifier.weight(1f))
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
