package com.sugarbridge.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sugarbridge.app.settings.SettingsStore

/** После перезагрузки телефона и после обновления приложения снова включает минутный режим, если он был включён. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = SettingsStore(context).load()
        scheduleSync(context, settings)
        if (settings.liveEnabled) LiveSyncService.start(context)
    }
}
