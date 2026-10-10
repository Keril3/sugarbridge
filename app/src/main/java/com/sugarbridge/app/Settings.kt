package com.sugarbridge.app

import android.content.Context
import androidx.core.content.edit

/** Настройки приложения. Пороги диапазона хранятся в мг/дл, показываются в выбранных единицах. */
data class AppSettings(
    val backgroundMinutes: Int = 15,     // фоновая синхронизация через WorkManager; 0 — выключена
    val liveEnabled: Boolean = false,    // минутный режим: сервис переднего плана
    val liveMinutes: Int = 1,            // как часто сервис спрашивает LibreLinkUp
    val liveShowValue: Boolean = true,   // сахар в уведомлении и в строке состояния
    val liveKeepAwake: Boolean = true,   // не давать процессору засыпать между опросами
    val unit: GlucoseUnit = GlucoseUnit.MMOL,
    val lowMgDl: Double = 70.0,          // 3,9 ммоль/л
    val highMgDl: Double = 180.0,        // 10,0 ммоль/л
) {
    companion object {
        val BACKGROUND_CHOICES = listOf(15, 30, 60, 180, 0)   // 15 минут — минимум WorkManager
        val LIVE_CHOICES = listOf(1, 2, 5, 10)
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            backgroundMinutes = prefs.getInt("background_minutes", d.backgroundMinutes),
            liveEnabled = prefs.getBoolean("live_enabled", d.liveEnabled),
            liveMinutes = prefs.getInt("live_minutes", d.liveMinutes),
            liveShowValue = prefs.getBoolean("live_show_value", d.liveShowValue),
            liveKeepAwake = prefs.getBoolean("live_keep_awake", d.liveKeepAwake),
            unit = GlucoseUnit.entries.getOrElse(prefs.getInt("unit", d.unit.ordinal)) { d.unit },
            lowMgDl = prefs.getFloat("low_mgdl", d.lowMgDl.toFloat()).toDouble(),
            highMgDl = prefs.getFloat("high_mgdl", d.highMgDl.toFloat()).toDouble(),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit {
            putInt("background_minutes", s.backgroundMinutes)
            putBoolean("live_enabled", s.liveEnabled)
            putInt("live_minutes", s.liveMinutes)
            putBoolean("live_show_value", s.liveShowValue)
            putBoolean("live_keep_awake", s.liveKeepAwake)
            putInt("unit", s.unit.ordinal)
            putFloat("low_mgdl", s.lowMgDl.toFloat())
            putFloat("high_mgdl", s.highMgDl.toFloat())
        }
    }

    fun update(change: (AppSettings) -> AppSettings): AppSettings = change(load()).also(::save)

    companion object {
        const val NAME = "settings"
    }
}
