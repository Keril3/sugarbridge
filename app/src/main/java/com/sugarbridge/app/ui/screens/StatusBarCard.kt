package com.sugarbridge.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.sync.IconBundling
import com.sugarbridge.app.sync.StatusIcon
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.SwitchRow
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import java.text.NumberFormat
import java.util.Locale

/** "×0,8" or "×1.25": a size step as a multiplier, written the way the language writes numbers. */
fun scaleLabel(scale: Float, locale: Locale): String =
    "×" + NumberFormat.getInstance(locale).apply { maximumFractionDigits = 2 }.format(scale)

/**
 * The glucose in the status bar: whether the number and the arrow are there, where the arrow stands (over the
 * number in one icon, or beside it in an icon of its own) and how much larger or smaller than usual each is.
 * The tiles show both ways drawn by the same code as the real icons. Works with minute mode and "Glucose in
 * the notification" on. Each switch only rules its own part: without the number the arrow stays, alone.
 */
@Composable
fun StatusBarCard(s: AppSettings, latest: Reading?, update: ((AppSettings) -> AppSettings) -> Unit) {
    val active = s.liveEnabled && s.liveShowValue
    val locale = currentLocale()
    val context = LocalContext.current
    var bundled by remember { mutableStateOf(IconBundling.bundled(context)) }
    XpWindow(stringResource(R.string.settings_statusbar_title)) {
        if (!active) Hint(stringResource(R.string.settings_statusbar_off))
        SwitchRow(
            stringResource(R.string.settings_status_value),
            stringResource(R.string.settings_status_value_hint),
            checked = s.liveStatusValue,
            onChange = { on -> update { it.copy(liveStatusValue = on) } },
            enabled = active,
        )
        SwitchRow(
            stringResource(R.string.settings_arrow),
            stringResource(R.string.settings_arrow_hint),
            checked = s.liveArrow,
            onChange = { on -> update { it.copy(liveArrow = on) } },
            enabled = active,
        )
        val number = active && s.liveStatusValue
        val arrow = active && s.liveArrow
        val both = number && arrow
        val value = formatGlucose(latest?.mgDl ?: 115.0, s.unit, locale)
        val trend = latest?.trend?.takeIf { it in 1..5 } ?: 4
        if (active && !s.liveStatusValue && !s.liveArrow) Hint(stringResource(R.string.settings_statusbar_tenshi))

        // where the arrow stands matters only with both: over the number in one icon, or beside it in its own
        Text(stringResource(R.string.settings_arrow_place), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (both) 1f else 0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(true, false).forEach { above ->
                val icons = if (above) {
                    listOf(rememberBitmap(value, trend, s.statusNumberSize, s.statusArrowSize) { StatusIcon.above(value, trend, s.statusNumberSize, s.statusArrowSize) })
                } else {
                    listOfNotNull(
                        rememberBitmap(value, s.statusNumberSize) { StatusIcon.number(value, s.statusNumberSize) },
                        rememberBitmap(trend, s.statusArrowSize) { StatusIcon.arrow(trend, s.statusArrowSize) },
                    )
                }
                PlaceTile(
                    label = stringResource(if (above) R.string.arrow_above else R.string.arrow_beside),
                    icons = icons,
                    selected = s.statusArrowAbove == above,
                    enabled = both,
                    modifier = Modifier.weight(1f).testTag(if (above) "place-above" else "place-beside"),
                ) {
                    // choosing "Beside it" tries two icons again, also on a phone that bundled them before
                    if (!above) IconBundling.reset(context).also { bundled = false }
                    update { it.copy(statusArrowAbove = above) }
                }
            }
        }
        Hint(stringResource(R.string.settings_arrow_place_hint), Modifier.alpha(if (both) 1f else 0.5f))
        if (both && bundled && !s.statusArrowAbove) Hint(stringResource(R.string.settings_icons_bundled), Modifier.testTag("icons-bundled"))
        Text(stringResource(R.string.settings_number_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (number) 1f else 0.5f))
        ChoiceRow(
            options = StatusIcon.SCALES.indices.toList(),
            selected = s.statusNumberSize,
            label = { scaleLabel(StatusIcon.SCALES[it], locale) },
            onSelect = { step -> update { it.copy(statusNumberSize = step) } },
            enabled = number,
        )
        Text(stringResource(R.string.settings_arrow_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (arrow) 1f else 0.5f))
        ChoiceRow(
            options = StatusIcon.SCALES.indices.toList(),
            selected = s.statusArrowSize,
            label = { scaleLabel(StatusIcon.SCALES[it], locale) },
            onSelect = { step -> update { it.copy(statusArrowSize = step) } },
            enabled = arrow,
        )
    }
}

@Composable
private fun rememberBitmap(vararg keys: Any, draw: () -> Bitmap?): ImageBitmap? =
    remember(*keys) { draw()?.asImageBitmap() }

/** The status bar of the tiles: the deep blue of the sky (navy at night), so the white icons read as on a phone. */
@Composable
private fun barBrush() = Brush.verticalGradient(
    if (LocalNight.current) listOf(Color(0xFF34468C), Color(0xFF162052)) else listOf(Color(0xFF4FA8E8), Color(0xFF2E7FE0))
)

/** One way to place the arrow, drawn as it will look: one icon, or two side by side. The chosen one has a glossy frame. */
@Composable
private fun PlaceTile(label: String, icons: List<ImageBitmap?>, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val xp = LocalXp.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Tenshi.ButtonBorder else xp.dots.copy(alpha = 0.6f), shape)
            .background(if (selected) xp.accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)).background(barBrush()),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icons.filterNotNull().forEach { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
