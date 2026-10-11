package com.sugarbridge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.TARGET_MAX_MGDL
import com.sugarbridge.app.glucose.TARGET_MIN_MGDL
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.glucose.isValidTarget
import com.sugarbridge.app.glucose.parseGlucose
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.LANGUAGES
import com.sugarbridge.app.settings.ThemeMode
import com.sugarbridge.app.settings.appLanguage
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.BottomSpace
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.DottedDivider
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.OutlinedTitle
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.ScreenColumn
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.SwitchRow
import com.sugarbridge.app.ui.components.XpMeter
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.minutesText
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, actions: AppActions) {
    val ui = vm.ui
    val s = ui.settings

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(2.dp))
        OutlinedTitle(stringResource(R.string.settings_title).uppercase(currentLocale()), style = MaterialTheme.typography.headlineSmall)

        XpWindow("LibreLinkUp") {
            Text(ui.email ?: "—", style = MaterialTheme.typography.bodyLarge)
            ButtonRow {
                PrimaryButton(stringResource(R.string.settings_check), onClick = vm::checkLibre, loading = ui.checking)
                SecondaryButton(stringResource(R.string.settings_sign_out), onClick = vm::signOut, danger = true)
            }
        }

        XpWindow(stringResource(R.string.settings_hc_title)) {
            Text(
                stringResource(
                    when (ui.canWrite) {
                        true -> R.string.settings_hc_allowed
                        false -> R.string.settings_hc_denied
                        null -> R.string.settings_hc_checking
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (ui.canWrite == false) MaterialTheme.colorScheme.error else LocalXp.current.text,
            )
            // once writing is allowed there is nothing to ask for: Health Connect itself changes permissions
            if (ui.canWrite == false) {
                PrimaryButton(stringResource(R.string.settings_permissions), onClick = actions::requestHealthPermission, modifier = Modifier.fillMaxWidth())
            }
            ButtonRow {
                SecondaryButton(stringResource(R.string.settings_open_hc), onClick = actions::openHealthConnect)
                if (ui.samsungHealth) SecondaryButton(stringResource(R.string.settings_open_samsung), onClick = actions::openSamsungHealth)
            }
            if (ui.canWrite == true) Hint(stringResource(R.string.settings_hc_manage))
            Hint(stringResource(R.string.settings_hc_hint))
        }

        XpWindow(stringResource(R.string.settings_live_title)) {
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
                stringResource(R.string.settings_pinned),
                stringResource(R.string.settings_pinned_hint),
                checked = s.livePinned,
                onChange = { on -> vm.updateSettings { it.copy(livePinned = on) } },
                enabled = s.liveEnabled,
            )
            SwitchRow(
                stringResource(R.string.settings_keep_awake),
                stringResource(R.string.settings_keep_awake_hint),
                checked = s.liveKeepAwake,
                onChange = { on -> vm.updateSettings { it.copy(liveKeepAwake = on) } },
                enabled = s.liveEnabled,
            )
            SecondaryButton(stringResource(R.string.settings_hide_notification), onClick = actions::hideNotification, enabled = s.liveEnabled, modifier = Modifier.fillMaxWidth())
            if (!ui.batteryUnrestricted) {
                PrimaryButton(stringResource(R.string.settings_battery), onClick = actions::requestBatteryUnrestricted, modifier = Modifier.fillMaxWidth())
            }
            Hint(stringResource(R.string.settings_live_hint))
        }

        XpWindow(stringResource(R.string.settings_background_title)) {
            // first the answer to "do I need this for the minute mode?"
            Text(stringResource(R.string.settings_background_intro), style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = AppSettings.BACKGROUND_CHOICES,
                selected = s.backgroundMinutes,
                label = { if (it == 0) stringResource(R.string.settings_off) else minutesText(it) },
                onSelect = { minutes -> vm.updateSettings { it.copy(backgroundMinutes = minutes) } },
            )
            Hint(stringResource(R.string.settings_background_hint))
            if (!ui.batteryUnrestricted) {
                Hint(stringResource(R.string.battery_text))
                PrimaryButton(stringResource(R.string.settings_battery), onClick = actions::requestBatteryUnrestricted, modifier = Modifier.fillMaxWidth())
            }
        }

        XpWindow(stringResource(R.string.settings_display_title)) {
            ChoiceRow(
                options = GlucoseUnit.entries.toList(),
                selected = s.unit,
                label = { stringResource(it.label) },
                onSelect = { unit -> vm.updateSettings { it.copy(unit = unit) } },
            )
            TargetRange(s) { low, high -> vm.updateSettings { it.copy(lowMgDl = low, highMgDl = high) } }
        }

        XpWindow(stringResource(R.string.theme_card)) {
            ChoiceRow(
                options = ThemeMode.entries.toList(),
                selected = s.theme,
                label = {
                    stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                    )
                },
                onSelect = { mode -> vm.updateSettings { it.copy(theme = mode) } },
            )
        }

        LanguageCard(actions)
        AboutCard(vm, actions)
        BottomSpace()
    }
}

/** Every language in its own name; the first option follows the phone. The screen redraws in the new language in place. */
@Composable
private fun LanguageCard(actions: AppActions) {
    val context = LocalContext.current
    val current = remember(currentLocale()) { appLanguage(context)?.tag }
    XpWindow(stringResource(R.string.language_card)) {
        val system = stringResource(R.string.language_system)
        ChoiceRow(
            options = listOf<String?>(null) + LANGUAGES.map { it.tag },
            selected = current,
            label = { tag -> LANGUAGES.firstOrNull { it.tag == tag }?.name ?: system },
            onSelect = actions::setLanguage,
        )
    }
}

/** The version, the GitHub page and updates from GitHub Releases. */
@Composable
private fun AboutCard(vm: MainViewModel, actions: AppActions) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val update = vm.update
    XpWindow(stringResource(R.string.settings_about_title)) {
        Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge)
        val release = update.available
        val progress = update.progress
        when {
            progress != null -> {
                XpMeter(progress, Modifier.fillMaxWidth())
                Hint(stringResource(R.string.update_downloading, (progress * 100).roundToInt()))
            }
            release != null -> {
                Text(stringResource(R.string.update_available, release.version), style = MaterialTheme.typography.bodyMedium, color = Tenshi.InRange)
                PrimaryButton(stringResource(R.string.update_install), onClick = actions::installUpdate, modifier = Modifier.fillMaxWidth())
                Hint(stringResource(R.string.update_play_protect))
            }
            update.checkedNothing -> Hint(stringResource(R.string.update_latest))
        }
        ButtonRow {
            SecondaryButton(
                if (update.checking) stringResource(R.string.update_checking) else stringResource(R.string.update_check),
                onClick = { vm.checkForUpdate() },
                enabled = !update.checking && progress == null,
            )
            SecondaryButton("GitHub", onClick = actions::openGitHub)
        }
        DottedDivider()
        Hint(stringResource(R.string.settings_about_hint))
    }
}

/**
 * Target range: the saved bounds on their own line, then two fields to type new ones in the chosen unit.
 * "Save" is active only for a range that makes sense.
 */
@Composable
private fun TargetRange(s: AppSettings, onDone: (Double, Double) -> Unit) {
    val xp = LocalXp.current
    val locale = currentLocale()
    val unit = stringResource(s.unit.label)
    val focus = LocalFocusManager.current
    var lowText by remember(s.lowMgDl, s.unit, locale) { mutableStateOf(formatGlucose(s.lowMgDl, s.unit, locale)) }
    var highText by remember(s.highMgDl, s.unit, locale) { mutableStateOf(formatGlucose(s.highMgDl, s.unit, locale)) }
    val low = parseGlucose(lowText, s.unit)
    val high = parseGlucose(highText, s.unit)
    val valid = isValidTarget(low, high)
    val changed = lowText != formatGlucose(s.lowMgDl, s.unit, locale) || highText != formatGlucose(s.highMgDl, s.unit, locale)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Tenshi.ButtonBorder,
        unfocusedBorderColor = xp.dots,
        focusedLabelColor = xp.accent,
        cursorColor = xp.accent,
        focusedTextColor = xp.text,
        unfocusedTextColor = xp.text,
    )
    val keyboard = KeyboardOptions(
        keyboardType = if (s.unit == GlucoseUnit.MMOL) KeyboardType.Decimal else KeyboardType.Number,
        imeAction = ImeAction.Done,
    )
    fun save() {
        if (valid && low != null && high != null) {
            onDone(low, high)
            focus.clearFocus()
        }
    }

    Text(
        stringResource(R.string.settings_target_range, formatGlucose(s.lowMgDl, s.unit, locale), formatGlucose(s.highMgDl, s.unit, locale), unit),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = lowText,
            onValueChange = { lowText = it.take(5) },
            label = { Text(stringResource(R.string.target_from)) },
            singleLine = true,
            colors = fieldColors,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = keyboard,
            keyboardActions = KeyboardActions(onDone = { save() }),
            isError = low == null,
            modifier = Modifier.weight(1f),
        )
        Text("–", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = highText,
            onValueChange = { highText = it.take(5) },
            label = { Text(stringResource(R.string.target_to)) },
            singleLine = true,
            colors = fieldColors,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = keyboard,
            keyboardActions = KeyboardActions(onDone = { save() }),
            isError = high == null,
            modifier = Modifier.weight(1f),
        )
        Text(unit, style = MaterialTheme.typography.labelMedium, color = xp.textMuted)
    }
    if (!valid) {
        Text(
            stringResource(
                R.string.target_invalid,
                formatGlucose(TARGET_MIN_MGDL, s.unit, locale),
                formatGlucose(TARGET_MAX_MGDL, s.unit, locale),
                unit,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    PrimaryButton(stringResource(R.string.target_save), onClick = ::save, enabled = valid && changed, modifier = Modifier.fillMaxWidth())
    Hint(stringResource(R.string.settings_target_hint))
}
