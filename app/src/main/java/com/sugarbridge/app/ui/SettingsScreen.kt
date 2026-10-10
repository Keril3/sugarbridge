package com.sugarbridge.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.AppSettings
import com.sugarbridge.app.GlucoseUnit
import com.sugarbridge.app.MGDL_PER_MMOL
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.formatGlucose
import com.sugarbridge.app.mmolOf
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, actions: AppActions) {
    val ui = vm.ui
    val s = ui.settings

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(4.dp))
        Text("Настройки", style = MaterialTheme.typography.headlineSmall.copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(Tenshi.Rainbow)))

        TenshiCard {
            CardTitle("LibreLinkUp")
            Text(ui.email ?: "—", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            ButtonRow {
                GradientButton("Проверить связь", onClick = vm::checkLibre, loading = ui.checking)
                SoftButton("Выйти", onClick = vm::signOut, danger = true)
            }
        }

        TenshiCard {
            CardTitle("Health Connect и Samsung Health")
            Text(
                when (ui.canWrite) {
                    true -> "Запись сахара разрешена ✓"
                    false -> "Нет разрешения на запись сахара"
                    null -> "Проверяю разрешение…"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (ui.canWrite == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            ButtonRow {
                SoftButton("Разрешения", onClick = actions::requestHealthPermission)
                SoftButton("Открыть Health Connect", onClick = actions::openHealthConnect)
                if (ui.samsungHealth) SoftButton("Открыть Samsung Health", onClick = actions::openSamsungHealth)
            }
            Hint("Samsung Health сам забирает сахар из Health Connect. Если точек нет — проверьте в Samsung Health → Настройки → Health Connect, что ему разрешено читать «Уровень глюкозы».")
        }

        TenshiCard {
            CardTitle("Фоновая синхронизация")
            ChoiceRow(
                options = AppSettings.BACKGROUND_CHOICES,
                selected = s.backgroundMinutes,
                label = { if (it == 0) "выкл" else minutesText(it) },
                onSelect = { minutes -> vm.updateSettings { it.copy(backgroundMinutes = minutes) } },
            )
            Hint(
                "Без уведомления. Каждый раз забирает историю за 12 часов: в ней точки примерно каждые 15 минут, " +
                    "поэтому пропусков не будет, даже если телефон спал. 15 минут — минимум, который разрешает Android."
            )
        }

        TenshiCard {
            CardTitle("Минутный режим")
            SwitchRow(
                "Обновлять сахар чаще",
                "В Samsung Health появляется каждый новый замер, а не только точки раз в 15 минут",
                checked = s.liveEnabled,
                onChange = actions::setLive,
            )
            ChoiceRow(
                options = AppSettings.LIVE_CHOICES,
                selected = s.liveMinutes,
                label = { if (it == 1) "каждую мин" else "раз в $it мин" },
                onSelect = { minutes -> vm.updateSettings { it.copy(liveMinutes = minutes) } },
                enabled = s.liveEnabled,
            )
            SwitchRow(
                "Сахар в уведомлении",
                "Число в строке состояния и в шторке. Выключите — уведомление станет тихим и спрячется вниз шторки",
                checked = s.liveShowValue,
                onChange = { on -> vm.updateSettings { it.copy(liveShowValue = on) } },
                enabled = s.liveEnabled,
            )
            SwitchRow(
                "Не давать телефону засыпать",
                "Интервал не растягивается при выключенном экране, но батарея садится быстрее",
                checked = s.liveKeepAwake,
                onChange = { on -> vm.updateSettings { it.copy(liveKeepAwake = on) } },
                enabled = s.liveEnabled,
            )
            SoftButton("Скрыть уведомление совсем", onClick = actions::hideNotification, enabled = s.liveEnabled, modifier = Modifier.fillMaxWidth())
            if (!ui.batteryUnrestricted) {
                GradientButton("Снять ограничения батареи", onClick = actions::requestBatteryUnrestricted, modifier = Modifier.fillMaxWidth())
            }
            Hint(
                "Чаще раза в 15 минут Android даёт работать в фоне только сервису с уведомлением. " +
                    "Уведомление можно выключить в настройках канала — режим продолжит работать, приложение останется в списке активных."
            )
        }

        TenshiCard {
            CardTitle("Отображение")
            ChoiceRow(
                options = GlucoseUnit.entries.toList(),
                selected = s.unit,
                label = { it.label },
                onSelect = { unit -> vm.updateSettings { it.copy(unit = unit) } },
            )
            TargetRange(s) { low, high -> vm.updateSettings { it.copy(lowMgDl = low, highMgDl = high) } }
        }

        TenshiCard {
            CardTitle("О SugarBridge")
            Hint(
                "Не медицинское изделие: тревоги о низком и высоком сахаре и решения о лечении — только по LibreLink. " +
                    "LibreLinkUp и LibreView используются через неофициальный API, только ваш аккаунт и ваши данные."
            )
        }
        BottomSpace()
    }
}

/** Целевой диапазон: ползунок в ммоль/л с шагом 0,1, подпись — в выбранных единицах. */
@Composable
private fun TargetRange(s: AppSettings, onDone: (Double, Double) -> Unit) {
    var range by remember(s.lowMgDl, s.highMgDl) {
        mutableStateOf(mmolOf(s.lowMgDl).toFloat()..mmolOf(s.highMgDl).toFloat())
    }
    val low = range.start * MGDL_PER_MMOL
    val high = range.endInclusive * MGDL_PER_MMOL
    Text(
        "Целевой диапазон: ${formatGlucose(low, s.unit)} – ${formatGlucose(high, s.unit)} ${s.unit.label}",
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
            activeTickColor = Color.Transparent,     // 119 делений по 0,1 — без точек на дорожке
            inactiveTickColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Hint("Только для цветов, графика и процента «в диапазоне». На тревоги LibreLink не влияет.")
}
