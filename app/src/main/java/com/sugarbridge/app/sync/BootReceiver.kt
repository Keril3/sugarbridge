package com.sugarbridge.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.settings.SettingsStore

/** After a reboot and after an app update, turns minute mode back on if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        DebugLog.add(context, "phone", if (intent.action == Intent.ACTION_BOOT_COMPLETED) "phone started" else "SugarBridge updated")
        val settings = SettingsStore(context).load()
        scheduleSync(context, settings)
        if (settings.liveEnabled) LiveSyncService.start(context)
    }
}
