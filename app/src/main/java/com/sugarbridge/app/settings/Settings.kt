package com.sugarbridge.app.settings

import android.content.Context
import androidx.core.content.edit
import com.sugarbridge.app.glucose.GlucoseUnit

/** Light or dark look; SYSTEM follows the phone. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App settings. The range bounds are stored in mg/dL and shown in the chosen unit. */
data class AppSettings(
    val backgroundMinutes: Int = 15,     // background sync through WorkManager; 0 = off
    val liveEnabled: Boolean = false,    // minute mode: a foreground service
    val liveMinutes: Int = 1,            // how often the service asks LibreLinkUp
    val liveShowValue: Boolean = true,   // glucose in the notification and the status bar
    val liveKeepAwake: Boolean = true,   // keep the CPU from sleeping between polls
    val livePinned: Boolean = false,     // the notification comes straight back if swiped away
    val unit: GlucoseUnit = GlucoseUnit.MMOL,
    val lowMgDl: Double = 70.0,          // 3.9 mmol/L
    val highMgDl: Double = 180.0,        // 10.0 mmol/L
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        val BACKGROUND_CHOICES = listOf(15, 30, 60, 180, 0)   // 15 minutes is the WorkManager minimum
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
            livePinned = prefs.getBoolean("live_pinned", d.livePinned),
            unit = GlucoseUnit.entries.getOrElse(prefs.getInt("unit", d.unit.ordinal)) { d.unit },
            lowMgDl = prefs.getFloat("low_mgdl", d.lowMgDl.toFloat()).toDouble(),
            highMgDl = prefs.getFloat("high_mgdl", d.highMgDl.toFloat()).toDouble(),
            theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: d.theme,
        )
    }

    fun save(s: AppSettings) {
        prefs.edit {
            putInt("background_minutes", s.backgroundMinutes)
            putBoolean("live_enabled", s.liveEnabled)
            putInt("live_minutes", s.liveMinutes)
            putBoolean("live_show_value", s.liveShowValue)
            putBoolean("live_keep_awake", s.liveKeepAwake)
            putBoolean("live_pinned", s.livePinned)
            putInt("unit", s.unit.ordinal)
            putFloat("low_mgdl", s.lowMgDl.toFloat())
            putFloat("high_mgdl", s.highMgDl.toFloat())
            putString("theme", s.theme.name)
        }
    }

    fun update(change: (AppSettings) -> AppSettings): AppSettings = change(load()).also(::save)

    /** The language screen is shown once, on the first start. The language itself is kept by Android. */
    var languageChosen: Boolean
        get() = prefs.getBoolean("language_chosen", false)
        set(value) = prefs.edit { putBoolean("language_chosen", value) }

    companion object {
        const val NAME = "settings"
    }
}
