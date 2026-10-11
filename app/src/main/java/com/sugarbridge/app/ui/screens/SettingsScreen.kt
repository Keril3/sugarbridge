package com.sugarbridge.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.MGDL_PER_MMOL
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.glucose.mmolOf
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.LANGUAGES
import com.sugarbridge.app.settings.appLanguage
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.BottomSpace
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.CardTitle
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.GradientButton
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.ScreenColumn
import com.sugarbridge.app.ui.components.SoftButton
import com.sugarbridge.app.ui.components.SwitchRow
import com.sugarbridge.app.ui.components.TenshiCard
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.minutesText
import com.sugarbridge.app.ui.theme.Tenshi
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, actions: AppActions) {
    val ui = vm.ui
    val s = ui.settings

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall.copy(brush = Brush.linearGradient(Tenshi.Rainbow)))

        TenshiCard {
            CardTitle("LibreLinkUp")
            Text(ui.email ?: "—", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            ButtonRow {
                GradientButton(stringResource(R.string.settings_check), onClick = vm::checkLibre, loading = ui.checking)
                SoftButton(stringResource(R.string.settings_sign_out), onClick = vm::signOut, danger = true)
            }
        }

        TenshiCard {
            CardTitle(stringResource(R.string.settings_hc_title))
            Text(
                stringResource(
                    when (ui.canWrite) {
                        true -> R.string.settings_hc_allowed
                        false -> R.string.settings_hc_denied
                        null -> R.string.settings_hc_checking
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (ui.canWrite == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            ButtonRow {
                SoftButton(stringResource(R.string.settings_permissions), onClick = actions::requestHealthPermission)
                SoftButton(stringResource(R.string.settings_open_hc), onClick = actions::openHealthConnect)
                if (ui.samsungHealth) SoftButton(stringResource(R.string.settings_open_samsung), onClick = actions::openSamsungHealth)
            }
            Hint(stringResource(R.string.settings_hc_hint))
        }

        TenshiCard {
            CardTitle(stringResource(R.string.settings_background_title))
            ChoiceRow(
                options = AppSettings.BACKGROUND_CHOICES,
                selected = s.backgroundMinutes,
                label = { if (it == 0) stringResource(R.string.settings_off) else minutesText(it) },
                onSelect = { minutes -> vm.updateSettings { it.copy(backgroundMinutes = minutes) } },
            )
            Hint(stringResource(R.string.settings_background_hint))
        }

        TenshiCard {
            CardTitle(stringResource(R.string.settings_live_title))
            SwitchRow(
                stringResource(R.string.settings_live_switch),
                stringResource(R.string.settings_live_switch_hint),
                checked = s.liveEnabled,
                onChange = actions::setLive,
            )
            ChoiceRow(
                options = AppSettings.LIVE_CHOICES,
                selected = s.liveMinutes,
                label = { if (it == 1) stringResource(R.string.settings_live_every_minute) else stringResource(R.string.settings_live_every, it) },
                onSelect = { minutes -> vm.updateSettings { it.copy(liveMinutes = minutes) } },
                enabled = s.liveEnabled,
            )
            SwitchRow(
                stringResource(R.string.settings_live_value),
                stringResource(R.string.settings_live_value_hint),
                checked = s.liveShowValue,
                onChange = { on -> vm.updateSettings { it.copy(liveShowValue = on) } },
                enabled = s.liveEnabled,
            )
            SwitchRow(
                stringResource(R.string.settings_keep_awake),
                stringResource(R.string.settings_keep_awake_hint),
                checked = s.liveKeepAwake,
                onChange = { on -> vm.updateSettings { it.copy(liveKeepAwake = on) } },
                enabled = s.liveEnabled,
            )
            SoftButton(stringResource(R.string.settings_hide_notification), onClick = actions::hideNotification, enabled = s.liveEnabled, modifier = Modifier.fillMaxWidth())
            if (!ui.batteryUnrestricted) {
                GradientButton(stringResource(R.string.settings_battery), onClick = actions::requestBatteryUnrestricted, modifier = Modifier.fillMaxWidth())
            }
            Hint(stringResource(R.string.settings_live_hint))
        }

        TenshiCard {
            CardTitle(stringResource(R.string.settings_display_title))
            ChoiceRow(
                options = GlucoseUnit.entries.toList(),
                selected = s.unit,
                label = { stringResource(it.label) },
                onSelect = { unit -> vm.updateSettings { it.copy(unit = unit) } },
            )
            TargetRange(s) { low, high -> vm.updateSettings { it.copy(lowMgDl = low, highMgDl = high) } }
        }

        LanguageCard(actions)

        TenshiCard {
            CardTitle(stringResource(R.string.settings_about_title))
            val context = LocalContext.current
            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
            Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Hint(stringResource(R.string.settings_about_hint))
        }
        BottomSpace()
    }
}

/** Every language in its own name; the first option follows the phone. Android recreates the screen in the new language. */
@Composable
private fun LanguageCard(actions: AppActions) {
    val context = LocalContext.current
    val current = remember(currentLocale()) { appLanguage(context)?.tag }
    TenshiCard {
        CardTitle(stringResource(R.string.language_card))
        val system = stringResource(R.string.language_system)
        ChoiceRow(
            options = listOf<String?>(null) + LANGUAGES.map { it.tag },
            selected = current,
            label = { tag -> LANGUAGES.firstOrNull { it.tag == tag }?.name ?: system },
            onSelect = actions::setLanguage,
        )
    }
}

/** Target range: a slider in mmol/L with a 0.1 step; the label is in the chosen unit. */
@Composable
private fun TargetRange(s: AppSettings, onDone: (Double, Double) -> Unit) {
    val locale = currentLocale()
    var range by remember(s.lowMgDl, s.highMgDl) {
        mutableStateOf(mmolOf(s.lowMgDl).toFloat()..mmolOf(s.highMgDl).toFloat())
    }
    val low = range.start * MGDL_PER_MMOL
    val high = range.endInclusive * MGDL_PER_MMOL
    Text(
        stringResource(R.string.settings_target_range, formatGlucose(low, s.unit, locale), formatGlucose(high, s.unit, locale), stringResource(s.unit.label)),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )
    RangeSlider(
        value = range,
        onValueChange = { r -> range = (r.start * 10).roundToInt() / 10f..(r.endInclusive * 10).roundToInt() / 10f },
        valueRange = 3.0f..15.0f,
        steps = 119,
        onValueChangeFinished = { onDone(range.start * MGDL_PER_MMOL, range.endInclusive * MGDL_PER_MMOL) },
        colors = SliderDefaults.colors(
            activeTrackColor = Tenshi.InRange,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTickColor = Color.Transparent,     // 119 steps of 0.1: no dots on the track
            inactiveTickColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Hint(stringResource(R.string.settings_target_hint))
}
