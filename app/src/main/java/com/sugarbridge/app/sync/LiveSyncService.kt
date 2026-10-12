package com.sugarbridge.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.R
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.GlucoseLevel
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.glucose.levelOf
import com.sugarbridge.app.glucose.text
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.libre.LoginFailed
import com.sugarbridge.app.libre.RateLimited
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.settings.minuteModeOff
import com.sugarbridge.app.ui.clockFormat
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Minute mode: a foreground service that syncs every N minutes and shows glucose in a notification.
 * Without such a service Android doesn't allow background work more often than every 15 minutes, and the
 * service must have a notification. It can be made quiet (no glucose) or turned off in the channel settings.
 */
class LiveSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var keepAwake: PowerManager.WakeLock? = null
    @Volatile private var hidden = false            // the notification was swiped away: don't bring it back until settings change
    @Volatile private var lastPosted: String? = null
    @Volatile private var lastSyncAt = 0L           // SystemClock.elapsedRealtime() of the last request
    @Volatile private var latest: Reading? = null
    @Volatile private var problem: Problem? = null  // the last failure, until a sync succeeds

    /**
     * The notification's time, fixed while the service runs. Android sorts ongoing notifications by it; a new
     * time on every update moved SugarBridge first each minute and back again when another app (a VPN, say)
     * updated its own. With a fixed time only the number and the arrow change.
     */
    private val shownSince = System.currentTimeMillis()

    /**
     * The app syncs outside this service too: when it is opened, with "Sync now" and in the background job.
     * Every saved sync redraws the notification at once, so it never lags behind the app.
     */
    private val syncListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SyncStore.KEY_OK_AT) {
            SyncStore(this).snapshot().recent.lastOrNull()?.let { newest ->
                if (latest == null || !newest.time.isBefore(latest!!.time)) latest = newest
            }
            problem = null
            safePost(SettingsStore(this).load())
        }
    }

    /** Doze and the screen go to the event log: they explain gaps between polls. */
    private val phoneListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = when (intent.action) {
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED ->
                    if (getSystemService(PowerManager::class.java).isDeviceIdleMode) "Doze on" else "Doze off"
                Intent.ACTION_SCREEN_ON -> "screen on"
                Intent.ACTION_SCREEN_OFF -> "screen off"
                else -> return
            }
            DebugLog.add(context, "phone", text)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        getSharedPreferences(SyncStore.NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(syncListener)
        val events = IntentFilter().apply {
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(phoneListener, events, RECEIVER_NOT_EXPORTED)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {   // the "Stop" button in the notification
                SettingsStore(this).update { it.minuteModeOff() }
                scheduleSync(this)   // the background sync minute mode switched off is back
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISSED -> {   // the notification was swiped away (Android 14+ allows it); the service keeps working
                if (SettingsStore(this).load().livePinned) {
                    // pinned: put it straight back, so it can't be lost by an accidental swipe
                    lastPosted = null
                    safePost(SettingsStore(this).load())
                } else {
                    hidden = true
                }
                return START_STICKY
            }
        }
        val settings = SettingsStore(this).load()
        if (!settings.liveEnabled) {   // e.g. the system restarted the service, but the mode has been turned off
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RELOAD) {   // settings changed: apply them right away
            hidden = false
            lastPosted = null
            loop?.cancel()
            loop = null
        }
        createNotificationChannels(this)
        if (latest == null) latest = SyncStore(this).snapshot().recent.lastOrNull()   // show the last known value at once
        try {
            startForeground(NOTIFICATION_ID, render(settings).second, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            // Android may refuse a start from the background. No harm: the background sync stays
            Log.e(SyncWorker.TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            Log.i(SyncWorker.TAG, "minute mode: every ${settings.liveMinutes} min, glucose in the notification: ${settings.liveShowValue}")
            DebugLog.add(this, "service", "minute mode on: every ${settings.liveMinutes} min, keep awake ${settings.liveKeepAwake}")
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    /** The app language changed: rename the channels and redraw the notification in it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        createNotificationChannels(this)
        lastPosted = null
        safePost(SettingsStore(this).load())
    }

    override fun onDestroy() {
        running = false
        DebugLog.add(this, "service", "minute mode off")
        runCatching { unregisterReceiver(phoneListener) }
        getSharedPreferences(SyncStore.NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(syncListener)
        scope.cancel()
        keepAwake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private suspend fun runLoop() {
        // after the loop restarts, don't poll LibreLinkUp more often than once a minute
        val sinceLast = SystemClock.elapsedRealtime() - lastSyncAt
        if (lastSyncAt > 0 && sinceLast < MIN_GAP_MS) delay(MIN_GAP_MS - sinceLast)
        while (currentCoroutineContext().isActive) {
            var settings = SettingsStore(this).load()
            holdWakeLock(settings)
            var pause = settings.liveMinutes * 60_000L
            val now = SystemClock.elapsedRealtime()
            // a gap much longer than planned means Android held the service back (Doze, battery limits)
            val trigger = if (lastSyncAt > 0) "minute, ${(now - lastSyncAt) / 1000} s after the previous" else "minute, first"
            lastSyncAt = now
            lastTick = System.currentTimeMillis()
            try {
                val result = withSyncWakeLock(settings) { syncAndRecord(this, trigger) }
                latest = result.latest ?: latest
                problem = null
                Log.i(SyncWorker.TAG, "minute mode: written ${result.written}")
            } catch (e: CancellationException) {
                throw e   // the service was stopped: leave the loop
            } catch (e: LoginFailed) {
                Libre.signOut(this)   // a wrong password is not retried, or the account could get locked
                finish(getString(R.string.notif_sign_in_again))
                return
            } catch (e: SyncBlocked) {
                if (e.code == Problem.Code.SIGNED_OUT) {   // nobody is signed in: nothing to poll
                    finish(getString(R.string.notif_sign_in))
                    return
                }
                problem = Problem.of(e)
            } catch (e: RateLimited) {
                pause = maxOf(pause, e.retryAfter * 1000L)   // wait as long as asked
                problem = Problem.of(e)
            } catch (e: Exception) {
                problem = Problem.of(e)
            }
            problem?.let { Log.w(SyncWorker.TAG, "minute mode: ${it.code} ${it.detail.orEmpty()}") }
            settings = SettingsStore(this).load()   // settings may have changed during the request
            safePost(settings)
            delay(pause)
        }
    }

    /** The service stops, the notification with the hint stays. */
    private fun finish(message: String) {
        val notification = builder(CHANNEL_LIVE, message, null).setOngoing(false).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /** Posting must never break the loop: a failure here would freeze the notification for good. */
    private fun safePost(settings: AppSettings) {
        try {
            post(settings)
        } catch (e: Exception) {
            Log.e(SyncWorker.TAG, "couldn't update the notification", e)
        }
    }

    @Synchronized
    private fun post(settings: AppSettings) {
        if (hidden) return
        val (key, notification) = render(settings)
        if (key == lastPosted) return   // nothing changed: don't bother the system
        lastPosted = key
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    /** The notification for the current state and a key that shows whether it changed. */
    private fun render(s: AppSettings): Pair<String, Notification> {
        val channel = if (s.liveShowValue) CHANNEL_LIVE else CHANNEL_QUIET
        problem?.let { p ->   // failures are reported in the quiet mode too
            val (title, text) = describe(p)
            return "problem|$channel|$title|$text" to builder(channel, title, text).build()
        }
        val r = latest
        if (s.liveShowValue && r != null) {
            val value = formatGlucose(r.mgDl, s.unit)
            val trend = if (s.liveArrow) r.trend else 0
            val title = r.copy(trend = trend).text(getString(s.unit.label), s.unit)
            val time = r.time.atZone(ZoneId.systemDefault()).format(clockFormat(this))
            val level = when (levelOf(r.mgDl, s.lowMgDl, s.highMgDl)) {
                GlucoseLevel.LOW -> R.string.level_low
                GlucoseLevel.IN_RANGE -> R.string.level_in_range
                GlucoseLevel.HIGH -> R.string.level_high
            }
            val text = getString(R.string.notif_value_text, time, getString(level))
            // the number itself as the status bar icon, drawn the way Settings show it
            val icon = if (s.liveStatusValue) {
                Icon.createWithBitmap(StatusIcon.draw(value, trend, s.statusArrowAbove, s.statusNumberSize, s.statusArrowSize))
            } else {
                null
            }
            val look = "${s.liveStatusValue}|${s.statusArrowAbove}|${s.statusNumberSize}|${s.statusArrowSize}"
            return "value|$title|$text|$look" to builder(channel, title, text, icon).build()
        }
        val every = if (s.liveMinutes == 1) getString(R.string.notif_every_minute) else getString(R.string.notif_every, s.liveMinutes)
        return "quiet|$channel|$every" to builder(channel, getString(R.string.notif_watching), every).build()
    }

    /** Title and text of the notification about a failure, in the current language. */
    private fun describe(p: Problem): Pair<String, String?> = when (p.code) {
        Problem.Code.NO_HEALTH_CONNECT, Problem.Code.NO_PERMISSION, Problem.Code.SIGNED_OUT ->
            getString(R.string.notif_problem, p.text(resources)) to null
        Problem.Code.RATE_LIMITED -> getString(R.string.notif_wait) to p.text(resources)
        Problem.Code.NETWORK, Problem.Code.SERVER -> getString(R.string.notif_offline) to p.text(resources)
        else -> getString(R.string.notif_failed) to p.text(resources)
    }

    private fun builder(channel: String, title: String, text: String?, icon: Icon? = null): Notification.Builder {
        val openApp = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)   // don't stack copies of the screen
        val open = PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, action(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val dismissed = PendingIntent.getService(this, 2, action(ACTION_DISMISSED), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel)
            .setSmallIcon(icon ?: Icon.createWithResource(this, R.drawable.ic_stat_tenshi))
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text) }
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .setOngoing(true)
            .setWhen(shownSince)      // the same time on every update: the notification keeps its place
            .setShowWhen(false)
            .setOnlyAlertOnce(true)   // update silently, without sound or vibration
            .setColor(0xFFF06FA4.toInt())
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stop).build())
    }

    private fun action(name: String) = Intent(this, LiveSyncService::class.java).setAction(name)

    /** "Keep the phone awake": the CPU doesn't sleep, so the pause between polls doesn't stretch. */
    private fun holdWakeLock(s: AppSettings) {
        if (s.liveKeepAwake) {
            val lock = keepAwake ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SugarBridge:live")
                .apply { setReferenceCounted(false) }
                .also { keepAwake = it }
            lock.acquire((s.liveMinutes + 5) * 60_000L)   // extended on every round of the loop
        } else {
            keepAwake?.let { if (it.isHeld) it.release() }
        }
    }

    /** Without the permanent wake lock, keep the CPU awake at least for the request itself. */
    private suspend fun <T> withSyncWakeLock(s: AppSettings, block: suspend () -> T): T {
        if (s.liveKeepAwake) return block()
        val lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SugarBridge:sync")
        lock.acquire(60_000L)
        try {
            return block()
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    companion object {
        /** For the developer tools: whether the service runs in this process and when it last asked LibreLinkUp. */
        @Volatile var running = false
            private set
        @Volatile var lastTick = 0L
            private set

        // "live" was used by version 1.0.0 with LOW importance: such notifications get no status bar icon,
        // and an app can't change the importance of an existing channel, hence the new id
        const val CHANNEL_LIVE = "glucose"
        internal const val LEGACY_CHANNEL = "live"
        const val CHANNEL_QUIET = "quiet"
        const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"
        internal const val ACTION_DISMISSED = "dismissed"
        private const val ACTION_RELOAD = "reload"
        private const val MIN_GAP_MS = 55_000L

        fun start(context: Context) = send(context, null)

        /** Applies new settings to the running service, or starts it. */
        fun reload(context: Context) = send(context, ACTION_RELOAD)

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveSyncService::class.java))
        }

        private fun send(context: Context, action: String?) {
            try {
                context.startForegroundService(Intent(context, LiveSyncService::class.java).setAction(action))
            } catch (e: Exception) {
                Log.e(SyncWorker.TAG, "couldn't start minute mode", e)
            }
        }
    }
}

/**
 * Notification channels of minute mode. Also created when the app starts: a channel's settings can't be opened
 * before it exists. Creating them again renames them to the current language.
 */
fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.deleteNotificationChannel(LiveSyncService.LEGACY_CHANNEL)
    // default importance, or Android won't show the number in the status bar; sound and vibration are off
    manager.createNotificationChannel(
        NotificationChannel(LiveSyncService.CHANNEL_LIVE, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_live_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
    )
    manager.createNotificationChannel(
        NotificationChannel(LiveSyncService.CHANNEL_QUIET, context.getString(R.string.channel_quiet), NotificationManager.IMPORTANCE_MIN).apply {
            description = context.getString(R.string.channel_quiet_description)
            setShowBadge(false)
        }
    )
}
