package com.sugarbridge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.UiState
import com.sugarbridge.app.UpdateUi
import com.sugarbridge.app.glucose.GlucoseLevel
import com.sugarbridge.app.glucose.GmiResult
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.glucose.levelOf
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.ago
import com.sugarbridge.app.ui.components.BottomSpace
import com.sugarbridge.app.ui.components.ChartDialog
import com.sugarbridge.app.ui.components.DottedDivider
import com.sugarbridge.app.ui.components.GlucoseChart
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Mascot
import com.sugarbridge.app.ui.components.Mood
import com.sugarbridge.app.ui.components.OutlinedTitle
import com.sugarbridge.app.ui.components.Pill
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.ScreenColumn
import com.sugarbridge.app.ui.components.SwitchRow
import com.sugarbridge.app.ui.components.TrendArrow
import com.sugarbridge.app.ui.components.XpMeter
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.minutesText
import com.sugarbridge.app.ui.rememberClockFormat
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.color
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel, actions: AppActions) {
    val ui = vm.ui
    val s = ui.settings
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {   // "N min ago" updates by itself
            delay(20_000)
            now = Instant.now()
        }
    }
    var chartOpen by remember { mutableStateOf(false) }
    val latest = ui.sync.recent.lastOrNull()

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTitle(greeting().uppercase(currentLocale()), style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing * 4))
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodySmall, color = LocalXp.current.text)
        }
        UpdateBanner(vm.update, actions)
        if (ui.canWrite == false) {
            XpWindow(stringResource(R.string.home_permission_title)) {
                Text(stringResource(R.string.home_permission_text), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(stringResource(R.string.home_permission_button), onClick = actions::requestHealthPermission, modifier = Modifier.fillMaxWidth())
                Hint(stringResource(R.string.home_permission_hint))
            }
        }
        // without the exemption Android holds the background sync back for hours while the phone lies idle
        if (!ui.batteryUnrestricted && (s.backgroundMinutes > 0 || s.liveEnabled)) {
            XpWindow(stringResource(R.string.battery_title)) {
                Text(stringResource(R.string.battery_text), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(stringResource(R.string.settings_battery), onClick = actions::requestBatteryUnrestricted, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!ui.samsungHealth) {
            XpWindow(stringResource(R.string.home_no_samsung_title)) {
                Hint(stringResource(R.string.home_no_samsung_hint))
            }
        }
        GlucoseHero(latest, s, now)
        if (ui.sync.recent.size >= 2) {
            XpWindow(stringResource(R.string.home_chart_title), onBodyClick = { chartOpen = true }) {
                GlucoseChart(ui.sync.recent, s.lowMgDl, s.highMgDl, s.unit)
                DayStats(ui.sync.recent, s)
                Hint(stringResource(R.string.chart_hint))
            }
        }
        if (ui.gmi90 != null) GmiCard(ui.gmi14, ui.gmi90)
        SyncCard(ui, now, vm, actions)
        BottomSpace()
    }

    if (chartOpen) {
        ChartDialog(stringResource(R.string.home_chart_title), ui.sync.recent, s.lowMgDl, s.highMgDl, s.unit) { chartOpen = false }
    }
}

@Composable
private fun greeting(): String = stringResource(
    when (LocalTime.now().hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_day
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
)

@Composable
fun levelText(level: GlucoseLevel): String = stringResource(
    when (level) {
        GlucoseLevel.LOW -> R.string.level_low
        GlucoseLevel.IN_RANGE -> R.string.level_in_range
        GlucoseLevel.HIGH -> R.string.level_high
    }
)

/** A newer version is on GitHub: one button downloads it and hands it to Android to install. */
@Composable
fun UpdateBanner(update: UpdateUi, actions: AppActions, modifier: Modifier = Modifier) {
    val release = update.available ?: return
    XpWindow(stringResource(R.string.update_title), modifier) {
        Text(stringResource(R.string.update_available, release.version), style = MaterialTheme.typography.bodyMedium)
        val progress = update.progress
        if (progress != null) {
            XpMeter(progress, Modifier.fillMaxWidth())
            Hint(stringResource(R.string.update_downloading, (progress * 100).roundToInt()))
        } else {
            PrimaryButton(stringResource(R.string.update_install), onClick = actions::installUpdate, modifier = Modifier.fillMaxWidth())
            Hint(stringResource(R.string.update_play_protect))
        }
    }
}

@Composable
private fun GlucoseHero(latest: Reading?, s: AppSettings, now: Instant) {
    val locale = currentLocale()
    XpWindow("tenshi.exe") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val level = latest?.let { levelOf(it.mgDl, s.lowMgDl, s.highMgDl) }
            val mood = when (level) {
                null -> Mood.SLEEPY
                GlucoseLevel.LOW -> Mood.LOW
                GlucoseLevel.IN_RANGE -> Mood.HAPPY
                GlucoseLevel.HIGH -> Mood.HIGH
            }
            Mascot(mood, size = 116.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                if (latest == null || level == null) {
                    Text(stringResource(R.string.home_no_data), style = MaterialTheme.typography.titleLarge)
                    Hint(stringResource(R.string.home_no_data_hint))
                } else {
                    val color = level.color()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatGlucose(latest.mgDl, s.unit, locale), style = MaterialTheme.typography.displayMedium, color = color)
                        Spacer(Modifier.width(4.dp))
                        TrendArrow(latest.trend, color, 34.dp)
                    }
                    Text(stringResource(s.unit.label), style = MaterialTheme.typography.titleSmall, color = LocalXp.current.textMuted)
                    Spacer(Modifier.height(6.dp))
                    Pill(levelText(level), color)
                }
            }
        }
        if (latest != null) {
            val time = latest.time.atZone(ZoneId.systemDefault()).format(rememberClockFormat())
            val age = Duration.between(latest.time, now)
            DottedDivider()
            Hint(stringResource(R.string.home_reading_at, time, ago(age)))
            if (age > Duration.ofMinutes(10)) {
                Text(stringResource(R.string.home_stale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Min / average / max and time in range over the shown period. */
@Composable
private fun DayStats(readings: List<Reading>, s: AppSettings) {
    val locale = currentLocale()
    val values = readings.map { it.mgDl }
    val inRange = (100.0 * readings.count { levelOf(it.mgDl, s.lowMgDl, s.highMgDl) == GlucoseLevel.IN_RANGE } / readings.size).roundToInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(stringResource(R.string.stat_min), formatGlucose(values.min(), s.unit, locale))
        Stat(stringResource(R.string.stat_avg), formatGlucose(values.average(), s.unit, locale))
        Stat(stringResource(R.string.stat_max), formatGlucose(values.max(), s.unit, locale))
        Stat(stringResource(R.string.stat_in_range), stringResource(R.string.percent, inRange))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalXp.current.textMuted)
    }
}

/** Estimated HbA1c (GMI) for 14 and 90 days, from all SugarBridge readings in Health Connect. */
@Composable
private fun GmiCard(days14: GmiResult?, days90: GmiResult?) {
    XpWindow(stringResource(R.string.gmi_title)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            GmiStat(stringResource(R.string.gmi_14_days), days14, Modifier.weight(1f))
            GmiStat(stringResource(R.string.gmi_90_days), days90, Modifier.weight(1f))
        }
        Hint(stringResource(R.string.gmi_hint, (GmiResult.MIN_COVERAGE * 100).roundToInt()))
    }
}

@Composable
private fun GmiStat(label: String, gmi: GmiResult?, modifier: Modifier = Modifier) {
    val xp = LocalXp.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (gmi == null) "—" else stringResource(R.string.gmi_percent, gmi.percent),
            style = MaterialTheme.typography.headlineSmall,
            color = if (gmi?.enough == true) xp.text else xp.textMuted,
        )
        if (gmi != null) {
            Text(stringResource(R.string.gmi_mmol_mol, gmi.mmolPerMol), style = MaterialTheme.typography.labelSmall, color = xp.textMuted)
            XpMeter(gmi.coverage.toFloat(), Modifier.padding(horizontal = 12.dp).fillMaxWidth(), color = if (gmi.enough) com.sugarbridge.app.ui.theme.Tenshi.InRange else com.sugarbridge.app.ui.theme.Tenshi.Low)
        }
        val coverage = gmi?.let { (it.coverage * 100).roundToInt() } ?: 0
        Text(
            when {
                gmi == null -> stringResource(R.string.gmi_no_data, label)
                gmi.enough -> stringResource(R.string.gmi_coverage, label, coverage)
                else -> stringResource(R.string.gmi_low_coverage, label, coverage)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (gmi?.enough == true) xp.textMuted else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SyncCard(ui: UiState, now: Instant, vm: MainViewModel, actions: AppActions) {
    val s = ui.settings
    val locale = currentLocale()
    XpWindow(stringResource(R.string.sync_title)) {
        val last = ui.sync.lastOkAt
        Text(
            if (last == null) stringResource(R.string.sync_never) else stringResource(R.string.sync_last, ago(Duration.between(last, now)), ui.sync.lastWritten),
            style = MaterialTheme.typography.bodyMedium,
        )
        val error = ui.sync.error
        if (error != null && (last == null || ui.sync.errorAt?.isAfter(last) == true)) {
            Text(
                stringResource(R.string.sync_error, error.text(LocalResources.current)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Hint(
            when {
                s.liveEnabled && s.liveMinutes == 1 -> stringResource(R.string.sync_live_every_minute)
                s.liveEnabled -> stringResource(R.string.sync_live_every, s.liveMinutes)
                s.backgroundMinutes > 0 -> stringResource(R.string.sync_background, minutesText(s.backgroundMinutes))
                else -> stringResource(R.string.sync_off)
            }
        )
        ui.sync.updateMinutes?.let { minutes ->
            Hint(stringResource(R.string.sync_update_rate, String.format(locale, "%.1f", minutes)))
        }
        ui.sync.deliveryMinutes?.let { minutes ->
            Hint(stringResource(R.string.sync_delivery, String.format(locale, "%.1f", minutes)))
        }
        PrimaryButton(
            stringResource(R.string.sync_now),
            onClick = { vm.syncNow() },
            enabled = ui.canWrite == true,
            loading = ui.syncing,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        SwitchRow(
            stringResource(R.string.live_title),
            stringResource(R.string.live_subtitle),
            checked = s.liveEnabled,
            onChange = actions::setLive,
        )
    }
}
