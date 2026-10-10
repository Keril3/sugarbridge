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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.UiState
import com.sugarbridge.app.glucose.GlucoseLevel
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.glucose.levelOf
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.BottomSpace
import com.sugarbridge.app.ui.components.CardTitle
import com.sugarbridge.app.ui.components.GlucoseChart
import com.sugarbridge.app.ui.components.GradientButton
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Mascot
import com.sugarbridge.app.ui.components.Mood
import com.sugarbridge.app.ui.components.Pill
import com.sugarbridge.app.ui.components.ScreenColumn
import com.sugarbridge.app.ui.components.SwitchRow
import com.sugarbridge.app.ui.components.TenshiCard
import com.sugarbridge.app.ui.components.TrendArrow
import com.sugarbridge.app.ui.theme.Tenshi
import com.sugarbridge.app.ui.theme.color
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel, actions: AppActions) {
    val ui = vm.ui
    val s = ui.settings
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {   // «N мин назад» обновляется само
            delay(20_000)
            now = Instant.now()
        }
    }
    val latest = ui.sync.recent.lastOrNull()

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(4.dp))
        Column {
            Text(greeting(), style = MaterialTheme.typography.headlineSmall.copy(brush = Brush.linearGradient(Tenshi.Rainbow)))
            Text("Тэнси-тян присматривает за сахаром", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ui.canWrite == false) {
            TenshiCard {
                CardTitle("Разрешите запись в Health Connect")
                Text("Без этого разрешения сахар не попадёт в Samsung Health.", style = MaterialTheme.typography.bodyMedium)
                GradientButton("Разрешить", onClick = actions::requestHealthPermission, modifier = Modifier.fillMaxWidth())
                Hint("И проверьте, что в Samsung Health → Настройки → Health Connect разрешено читать «Уровень глюкозы».")
            }
        }
        if (!ui.samsungHealth) {
            TenshiCard {
                CardTitle("Samsung Health не найден")
                Hint("Сахар всё равно будет попадать в Health Connect — его увидят и другие приложения. Установите Samsung Health, чтобы смотреть графики там.")
            }
        }
        GlucoseHero(latest, s, now)
        if (ui.sync.recent.size >= 2) {
            TenshiCard {
                CardTitle("Последние 12 часов")
                GlucoseChart(ui.sync.recent, s.lowMgDl, s.highMgDl, s.unit)
                DayStats(ui.sync.recent, s)
            }
        }
        SyncCard(ui, now, vm, actions)
        BottomSpace()
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Доброе утро ✧"
    in 12..17 -> "Добрый день ✧"
    in 18..22 -> "Добрый вечер ✧"
    else -> "Доброй ночи ✧"
}

@Composable
private fun GlucoseHero(latest: Reading?, s: AppSettings, now: Instant) {
    TenshiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val level = latest?.let { levelOf(it.mgDl, s.lowMgDl, s.highMgDl) }
            val mood = when (level) {
                null -> Mood.SLEEPY
                GlucoseLevel.LOW -> Mood.LOW
                GlucoseLevel.IN_RANGE -> Mood.HAPPY
                GlucoseLevel.HIGH -> Mood.HIGH
            }
            Mascot(mood, size = 108.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (latest == null || level == null) {
                    Text("Пока нет данных", style = MaterialTheme.typography.titleLarge)
                    Hint("Нажмите «Синхронизировать» — Тэнси-тян принесёт сахар из LibreLinkUp")
                } else {
                    val color = level.color()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatGlucose(latest.mgDl, s.unit),
                            style = MaterialTheme.typography.displayMedium.copy(brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.75f), color))),
                        )
                        Spacer(Modifier.width(4.dp))
                        TrendArrow(latest.trend, color, 36.dp)
                    }
                    Text(s.unit.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Pill(
                        when (level) {
                            GlucoseLevel.LOW -> "ниже диапазона"
                            GlucoseLevel.IN_RANGE -> "в диапазоне ♡"
                            GlucoseLevel.HIGH -> "выше диапазона"
                        },
                        color,
                    )
                }
            }
        }
        if (latest != null) {
            val time = latest.time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            val age = Duration.between(latest.time, now)
            Hint("Замер в $time · ${ago(age)}")
            if (age > Duration.ofMinutes(10)) {
                Text(
                    "Данные старше 10 минут — загляните в LibreLink. Тревоги о сахаре приходят только оттуда.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Мин / среднее / макс и время в диапазоне за показанный период. */
@Composable
private fun DayStats(readings: List<Reading>, s: AppSettings) {
    val values = readings.map { it.mgDl }
    val inRange = (100.0 * readings.count { levelOf(it.mgDl, s.lowMgDl, s.highMgDl) == GlucoseLevel.IN_RANGE } / readings.size).roundToInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("мин", formatGlucose(values.min(), s.unit))
        Stat("среднее", formatGlucose(values.average(), s.unit))
        Stat("макс", formatGlucose(values.max(), s.unit))
        Stat("в диапазоне", "$inRange%")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SyncCard(ui: UiState, now: Instant, vm: MainViewModel, actions: AppActions) {
    val s = ui.settings
    TenshiCard {
        CardTitle("Синхронизация")
        val last = ui.sync.lastOkAt
        Text(
            if (last == null) "Ещё не было" else "Последняя: ${ago(Duration.between(last, now))} · записано ${ui.sync.lastWritten}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val error = ui.sync.error
        if (error != null && (last == null || ui.sync.errorAt?.isAfter(last) == true)) {
            Text("Ошибка: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Hint(
            when {
                s.liveEnabled -> "Минутный режим: " + if (s.liveMinutes == 1) "каждую минуту ✧" else "раз в ${s.liveMinutes} мин ✧"
                s.backgroundMinutes > 0 -> "В фоне раз в ${minutesText(s.backgroundMinutes)}, без уведомления"
                else -> "Автоматическая синхронизация выключена"
            }
        )
        ui.sync.updateMinutes?.let { minutes ->
            Hint("LibreLinkUp присылает новое значение примерно раз в ${String.format(Locale.getDefault(), "%.1f", minutes)} мин")
        }
        GradientButton(
            "Синхронизировать сейчас",
            onClick = { vm.syncNow() },
            enabled = ui.canWrite == true,
            loading = ui.syncing,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        SwitchRow(
            "Минутный режим",
            "Свежий сахар в Samsung Health каждую минуту",
            checked = s.liveEnabled,
            onChange = actions::setLive,
        )
    }
}

fun ago(d: Duration): String {
    val minutes = d.toMinutes()
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч ${minutes % 60} мин назад"
        else -> "${minutes / (24 * 60)} дн назад"
    }
}

fun minutesText(minutes: Int): String = if (minutes < 60) "$minutes мин" else "${minutes / 60} ч"
