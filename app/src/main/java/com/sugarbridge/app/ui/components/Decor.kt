package com.sugarbridge.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.Tenshi
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Фон всего приложения: пастельное небо, облака внизу и мерцающие звёздочки. */
@Composable
fun AngelBackground(modifier: Modifier = Modifier, halo: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val night = LocalNight.current
    val sky = if (night) {
        listOf(Color(0xFF1E1630), Color(0xFF1D1934), Color(0xFF132039))
    } else {
        listOf(Color(0xFFFFEEF6), Color(0xFFF5EDFF), Color(0xFFE8F4FF))
    }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(sky))) {
        SkyDecor(night, halo)
        // у полупрозрачного фона Compose не угадает цвет текста — задаём сами, иначе ночью текст чёрный
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

private class Star(val x: Float, val y: Float, val radius: Float, val phase: Float, val color: Int)

@Composable
private fun SkyDecor(night: Boolean, halo: Boolean) {
    val stars = remember {
        val random = Random(7)
        List(26) { Star(random.nextFloat(), random.nextFloat() * 0.92f, 3f + random.nextFloat() * 6f, random.nextFloat(), it % 3) }
    }
    val twinkle by rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "phase",
    )
    val palette = if (night) {
        listOf(Color.White, Tenshi.HaloLight, Color(0xFFFFB8D6))
    } else {
        listOf(Color.White, Color(0xFFFFE08A), Color(0xFFFFB3D2))
    }
    // свой слой: мерцание перерисовывает только звёзды, а не весь экран
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        // облака внизу
        val cloud = if (night) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.7f)
        val w = size.width
        val h = size.height
        listOf(0.05f to 0.10f, 0.30f to 0.13f, 0.58f to 0.11f, 0.85f to 0.14f).forEach { (x, r) ->
            drawCircle(cloud, radius = w * r, center = Offset(w * x, h + w * r * 0.35f))
        }
        // большой бледный нимб в углу
        if (halo) drawOval(
            color = (if (night) Tenshi.Halo.copy(alpha = 0.10f) else Tenshi.Halo.copy(alpha = 0.22f)),
            topLeft = Offset(w * 0.62f, h * 0.035f),
            size = androidx.compose.ui.geometry.Size(w * 0.30f, w * 0.08f),
            style = Stroke(width = 3.dp.toPx()),
        )
        stars.forEach { s ->
            val glow = 0.5f + 0.5f * sin(2 * PI * (twinkle + s.phase)).toFloat()
            val alpha = (if (night) 0.25f else 0.45f) + 0.5f * glow
            sparkle(Offset(w * s.x, h * s.y), s.radius.dp.toPx() * (0.75f + 0.35f * glow), palette[s.color].copy(alpha = alpha.coerceAtMost(1f)))
        }
    }
}

/** Четырёхлучевая звёздочка с вогнутыми гранями ✧. */
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

/** Карточка: полупрозрачная, с радужной каёмкой и розовой тенью. */
@Composable
fun TenshiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val night = LocalNight.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (night) 0.dp else 10.dp, shape, ambientColor = Tenshi.Sakura.copy(alpha = 0.25f), spotColor = Tenshi.Sakura.copy(alpha = 0.3f)),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (night) 0.78f else 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Brush.linearGradient(Tenshi.Rainbow.map { it.copy(alpha = if (night) 0.35f else 0.55f) })),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Заголовок карточки со звёздочкой. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp)) { sparkle(center, size.minDimension / 2, Tenshi.Halo) }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Главная кнопка: «пилюля» с градиентом сакура → лаванда → небо. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val active = enabled && !loading
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(if (active) 6.dp else 0.dp, CircleShape, spotColor = Tenshi.SakuraDeep.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(Brush.horizontalGradient(Tenshi.Rainbow))
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

/** Второстепенная кнопка-«пилюля» с контуром. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, color.copy(alpha = if (enabled) 0.7f else 0.3f)),
    ) {
        Text(text, color = if (enabled) color else color.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge)
    }
}

/** Кнопки в ряд; не помещаются — переносятся целиком на следующую строку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Ряд вариантов-«конфеток», один выбран. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                label = { Text(label(option)) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** Строка настройки с переключателем. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** Цветная «пилюля» с подписью, например «в диапазоне ♡». */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Стрелка тренда, нарисованная, а не символом: 1 — вниз … 3 — вправо … 5 — вверх. */
@Composable
fun TrendArrow(trend: Int, color: Color, size: Dp = 40.dp) {
    if (trend !in 1..5) return
    val angle = (3 - trend) * 45f   // 3 — горизонтально, каждый шаг — 45°
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

/** Тонкий разделитель-«ленточка». */
@Composable
fun RibbonDivider(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(2.dp).clip(CircleShape)
            .background(Brush.horizontalGradient(Tenshi.Rainbow.map { it.copy(alpha = 0.35f) })),
    )
}

/** Подпись мелким серым текстом. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Отступ снизу, чтобы последний элемент не прятался под панелью вкладок. */
@Composable
fun BottomSpace() = Spacer(Modifier.height(24.dp))

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
