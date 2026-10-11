package com.sugarbridge.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import java.time.LocalTime

private val SIZE_STEPS = listOf("S", "M", "L", "XL")

/**
 * The glucose in the status bar: whether the number and the arrow are there, where the arrow stands and how
 * large both are, with a preview drawn by the same code as the real icon. Works with minute mode and
 * "Glucose in the notification" on.
 */
@Composable
fun StatusBarCard(s: AppSettings, latest: Reading?, update: ((AppSettings) -> AppSettings) -> Unit) {
    val active = s.liveEnabled && s.liveShowValue
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
        Text(stringResource(R.string.settings_number_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (icon) 1f else 0.5f))
        ChoiceRow(
            options = SIZE_STEPS.indices.toList(),
            selected = s.statusNumberSize,
            label = { SIZE_STEPS[it] },
            onSelect = { step -> update { it.copy(statusNumberSize = step) } },
            enabled = icon,
        )
        Text(stringResource(R.string.settings_arrow_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (arrow) 1f else 0.5f))
        ChoiceRow(
            options = SIZE_STEPS.indices.toList(),
            selected = s.statusArrowSize,
            label = { SIZE_STEPS[it] },
            onSelect = { step -> update { it.copy(statusArrowSize = step) } },
            enabled = arrow,
        )
        Text(stringResource(R.string.settings_arrow_place), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(if (arrow) 1f else 0.5f))
        ChoiceRow(
            options = listOf(true, false),
            selected = s.statusArrowAbove,
            label = { stringResource(if (it) R.string.arrow_above else R.string.arrow_beside) },
            onSelect = { above -> update { it.copy(statusArrowAbove = above) } },
            enabled = arrow,
        )
        if (icon) {
            StatusBarPreview(s, latest)
            Hint(stringResource(R.string.settings_statusbar_preview))
        }
    }
}

/** The icon as the status bar shows it, next to a clock, and the same icon enlarged. */
@Composable
private fun StatusBarPreview(s: AppSettings, latest: Reading?) {
    val locale = currentLocale()
    val value = formatGlucose(latest?.mgDl ?: 115.0, s.unit, locale)
    val trend = if (s.liveArrow) latest?.trend?.takeIf { it in 1..5 } ?: 4 else 0
    val bitmap = remember(value, trend, s.statusArrowAbove, s.statusNumberSize, s.statusArrowSize) {
        StatusIcon.draw(value, trend, s.statusArrowAbove, s.statusNumberSize, s.statusArrowSize).asImageBitmap()
    }
    val clock = rememberClockFormat()
    val bar = Color(0xFF14161C)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.background(bar, RoundedCornerShape(8.dp)).height(32.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(LocalTime.of(12, 30).format(clock), color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.SansSerif)
            Image(bitmap, contentDescription = null, modifier = Modifier.size(20.dp).testTag("statusbar-preview"))
        }
        Box(Modifier.background(bar, RoundedCornerShape(10.dp)).padding(8.dp)) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(64.dp))
        }
    }
}
