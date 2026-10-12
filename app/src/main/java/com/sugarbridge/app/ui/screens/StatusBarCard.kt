package com.sugarbridge.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.sync.StatusIcon
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.SwitchRow
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.rememberClockFormat
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import java.text.NumberFormat
import java.time.LocalTime
import java.util.Locale

/** "×0,8" or "×1.25": a size step as a multiplier, written the way the language writes numbers. */
fun scaleLabel(scale: Float, locale: Locale): String =
    "×" + NumberFormat.getInstance(locale).apply { maximumFractionDigits = 2 }.format(scale)

/**
 * The glucose in the status bar: whether the number and the arrow are there, where the arrow stands, how much
 * larger or smaller than usual each is, and a preview drawn by the same code as the real icon. Works with
 * minute mode and "Glucose in the notification" on.
 */
@Composable
fun StatusBarCard(s: AppSettings, latest: Reading?, update: ((AppSettings) -> AppSettings) -> Unit) {
    val active = s.liveEnabled && s.liveShowValue
    val locale = currentLocale()
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
        val icon = active && s.liveStatusValue
        val arrow = icon && s.liveArrow
        val value = formatGlucose(latest?.mgDl ?: 115.0, s.unit, locale)
        val trend = latest?.trend?.takeIf { it in 1..5 } ?: 4

        Text(stringResource(R.string.settings_arrow_place), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (arrow) 1f else 0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(true, false).forEach { above ->
                PlaceTile(
                    label = stringResource(if (above) R.string.arrow_above else R.string.arrow_beside),
                    bitmap = rememberIcon(value, trend, above, s.statusNumberSize, s.statusArrowSize),
                    selected = s.statusArrowAbove == above,
                    enabled = arrow,
                    modifier = Modifier.weight(1f).testTag(if (above) "place-above" else "place-beside"),
                ) { update { it.copy(statusArrowAbove = above) } }
            }
        }
        Text(stringResource(R.string.settings_number_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (icon) 1f else 0.5f))
        ChoiceRow(
            options = StatusIcon.SCALES.indices.toList(),
            selected = s.statusNumberSize,
            label = { scaleLabel(StatusIcon.SCALES[it], locale) },
            onSelect = { step -> update { it.copy(statusNumberSize = step) } },
            enabled = icon,
        )
        Text(stringResource(R.string.settings_arrow_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (arrow) 1f else 0.5f))
        ChoiceRow(
            options = StatusIcon.SCALES.indices.toList(),
            selected = s.statusArrowSize,
            label = { scaleLabel(StatusIcon.SCALES[it], locale) },
            onSelect = { step -> update { it.copy(statusArrowSize = step) } },
            enabled = arrow,
        )
        if (icon) {
            StatusBarPreview(rememberIcon(value, if (s.liveArrow) trend else 0, s.statusArrowAbove, s.statusNumberSize, s.statusArrowSize))
            Hint(stringResource(R.string.settings_statusbar_preview))
        }
    }
}

@Composable
private fun rememberIcon(value: String, trend: Int, above: Boolean, numberSize: Int, arrowSize: Int): ImageBitmap =
    remember(value, trend, above, numberSize, arrowSize) { StatusIcon.draw(value, trend, above, numberSize, arrowSize).asImageBitmap() }

/** The status bar of the previews: the deep blue of the sky (navy at night), so the white icon reads as on a phone. */
@Composable
private fun barBrush() = Brush.verticalGradient(
    if (LocalNight.current) listOf(Color(0xFF34468C), Color(0xFF162052)) else listOf(Color(0xFF4FA8E8), Color(0xFF2E7FE0))
)

/** One way to place the arrow, drawn as it will look; the chosen one has a glossy frame. */
@Composable
private fun PlaceTile(label: String, bitmap: ImageBitmap, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)).background(barBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(52.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

/** The icon in a status bar next to a clock, at its real size, and the same icon large. */
@Composable
private fun StatusBarPreview(bitmap: ImageBitmap) {
    val xp = LocalXp.current
    val clock = rememberClockFormat()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, xp.windowBorder, RoundedCornerShape(10.dp))
                .background(barBrush())
                .height(36.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(LocalTime.of(12, 30).format(clock), color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.SansSerif)
            Image(bitmap, contentDescription = null, modifier = Modifier.size(22.dp).testTag("statusbar-preview"))
        }
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, xp.windowBorder, RoundedCornerShape(10.dp)).background(barBrush()).padding(6.dp),
        ) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(68.dp))
        }
    }
}
