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
    val liveStatusValue: Boolean = true, // the number itself as the status bar icon, instead of Tenshi-chan
    val liveArrow: Boolean = true,       // the trend arrow next to the value in the notification and the status bar
    val statusArrowAbove: Boolean = true, // in the status bar icon: the arrow over the number (larger digits), or beside it
    val statusNumberSize: Int = 3,       // steps of StatusIcon.SCALES, the largest by default
    val statusArrowSize: Int = 2,        // steps of StatusIcon.SCALES
    val backgroundBeforeLive: Int = 0,   // the background interval minute mode switched off, given back when it stops
    val backgroundNotice: Boolean = false, // the notice that minute mode switched background sync off, until closed
    val unit: GlucoseUnit = GlucoseUnit.MMOL,
    val lowMgDl: Double = 70.0,          // 3.9 mmol/L
    val highMgDl: Double = 180.0,        // 10.0 mmol/L
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textScale: Float = 1f,           // on top of the phone's font size
    val lowVision: Boolean = false,      // a plain readable font, larger and with more contrast, no dots
    val developer: Boolean = false,      // the developer tools in Settings
    val autoUpdateCheck: Boolean = true, // ask GitHub for a new version by itself
    val updatePrompt: Boolean = true,    // offer it once in a window and, with the app closed, in a notification
) {
    companion object {
        val BACKGROUND_CHOICES = listOf(15, 30, 60, 180, 0)   // 15 minutes is the WorkManager minimum
        val LIVE_CHOICES = listOf(1, 2, 5, 10)
        val TEXT_SCALES = listOf(0.9f, 1f, 1.15f, 1.3f)
    }
}

/**
 * Minute mode on. The background job would only repeat what minute mode already does every minute, so it is
 * switched off; its interval is kept to come back when minute mode stops, and a notice tells about it.
 */
fun AppSettings.minuteModeOn(): AppSettings = when {
    liveEnabled -> this
    backgroundMinutes > 0 -> copy(liveEnabled = true, backgroundMinutes = 0, backgroundBeforeLive = backgroundMinutes, backgroundNotice = true)
    else -> copy(liveEnabled = true)
}

/** Minute mode off: the background sync it switched off comes back, so the glucose keeps coming. */
fun AppSettings.minuteModeOff(): AppSettings = when {
    !liveEnabled -> this
    backgroundMinutes == 0 && backgroundBeforeLive > 0 ->
        copy(liveEnabled = false, backgroundMinutes = backgroundBeforeLive, backgroundBeforeLive = 0, backgroundNotice = false)
    else -> copy(liveEnabled = false, backgroundBeforeLive = 0, backgroundNotice = false)
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
            liveStatusValue = prefs.getBoolean("live_status_value", d.liveStatusValue),
            liveArrow = prefs.getBoolean("live_arrow", d.liveArrow),
            statusArrowAbove = prefs.getBoolean("status_arrow_above", d.statusArrowAbove),
            statusNumberSize = prefs.getInt("status_number_size", d.statusNumberSize),
            statusArrowSize = prefs.getInt("status_arrow_size", d.statusArrowSize),
            backgroundBeforeLive = prefs.getInt("background_before_live", d.backgroundBeforeLive),
            backgroundNotice = prefs.getBoolean("background_notice", d.backgroundNotice),
            unit = GlucoseUnit.entries.getOrElse(prefs.getInt("unit", d.unit.ordinal)) { d.unit },
            lowMgDl = prefs.getFloat("low_mgdl", d.lowMgDl.toFloat()).toDouble(),
            highMgDl = prefs.getFloat("high_mgdl", d.highMgDl.toFloat()).toDouble(),
            theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: d.theme,
            textScale = prefs.getFloat("text_scale", d.textScale),
            lowVision = prefs.getBoolean("low_vision", d.lowVision),
            developer = prefs.getBoolean("developer", d.developer),
            autoUpdateCheck = prefs.getBoolean("auto_update_check", d.autoUpdateCheck),
            updatePrompt = prefs.getBoolean("update_prompt", d.updatePrompt),
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
            putBoolean("live_status_value", s.liveStatusValue)
            putBoolean("live_arrow", s.liveArrow)
            putBoolean("status_arrow_above", s.statusArrowAbove)
            putInt("status_number_size", s.statusNumberSize)
            putInt("status_arrow_size", s.statusArrowSize)
            putInt("background_before_live", s.backgroundBeforeLive)
            putBoolean("background_notice", s.backgroundNotice)
            putInt("unit", s.unit.ordinal)
            putFloat("low_mgdl", s.lowMgDl.toFloat())
            putFloat("high_mgdl", s.highMgDl.toFloat())
            putString("theme", s.theme.name)
            putFloat("text_scale", s.textScale)
            putBoolean("low_vision", s.lowVision)
            putBoolean("developer", s.developer)
            putBoolean("auto_update_check", s.autoUpdateCheck)
            putBoolean("update_prompt", s.updatePrompt)
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
