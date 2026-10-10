package com.sugarbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
 * Минутный режим: сервис переднего плана раз в N минут синхронизирует и показывает сахар в уведомлении.
 * Без такого сервиса Android не даёт работать в фоне чаще раза в 15 минут, а у сервиса обязано быть
 * уведомление. Его можно сделать тихим (без сахара) или совсем выключить в настройках канала.
 */
class LiveSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var keepAwake: PowerManager.WakeLock? = null
    @Volatile private var hidden = false            // уведомление смахнули — не возвращаем его до смены настроек
    @Volatile private var lastPosted: String? = null
    @Volatile private var lastSyncAt = 0L           // SystemClock.elapsedRealtime() последнего запроса
    @Volatile private var latest: Reading? = null
    @Volatile private var problem: Problem? = null  // последняя ошибка, пока не будет удачной синхронизации

    private class Problem(val title: String, val text: String?)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {   // кнопка «Остановить» в уведомлении
                SettingsStore(this).update { it.copy(liveEnabled = false) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISSED -> {   // уведомление смахнули (Android 14+ это позволяет) — сервис продолжает работать
                hidden = true
                return START_STICKY
            }
        }
        val settings = SettingsStore(this).load()
        if (!settings.liveEnabled) {   // например, система перезапустила сервис, а режим уже выключили
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RELOAD) {   // поменяли настройки — применяем сразу
            hidden = false
            lastPosted = null
            loop?.cancel()
            loop = null
        }
        createNotificationChannels(this)
        if (latest == null) latest = SyncStore(this).snapshot().recent.lastOrNull()   // сразу показать последнее известное
        try {
            startForeground(NOTIFICATION_ID, render(settings).second, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            // Android может запретить запуск из фона. Не страшно: останется фоновая синхронизация
            Log.e(SyncWorker.TAG, "startForeground не удался", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            Log.i(SyncWorker.TAG, "минутный режим: раз в ${settings.liveMinutes} мин, сахар в уведомлении: ${settings.liveShowValue}")
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        keepAwake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private suspend fun runLoop() {
        // после перезапуска цикла не опрашиваем LibreLinkUp чаще раза в минуту
        val sinceLast = SystemClock.elapsedRealtime() - lastSyncAt
        if (lastSyncAt > 0 && sinceLast < MIN_GAP_MS) delay(MIN_GAP_MS - sinceLast)
        while (currentCoroutineContext().isActive) {
            var settings = SettingsStore(this).load()
            holdWakeLock(settings)
            var pause = settings.liveMinutes * 60_000L
            lastSyncAt = SystemClock.elapsedRealtime()
            try {
                val result = withSyncWakeLock(settings) { syncAndRecord(this) }
                latest = result.latest ?: latest
                problem = null
                Log.i(SyncWorker.TAG, "минутный режим: записано ${result.written}")
            } catch (e: CancellationException) {
                throw e   // сервис остановили — выходим из цикла
            } catch (e: LoginFailed) {
                Libre.signOut(this)   // неверный пароль не повторяем, иначе можно нарваться на блокировку
                finish("Пароль LibreLinkUp не подошёл: откройте SugarBridge и войдите заново")
                return
            } catch (e: IllegalStateException) {
                if (Libre.client(this) == null) {   // не выполнен вход — опрашивать нечего
                    finish("Войдите в LibreLinkUp в приложении SugarBridge")
                    return
                }
                problem = Problem("SugarBridge: ${e.message}", null)
            } catch (e: RateLimited) {
                pause = maxOf(pause, e.retryAfter * 1000L)   // ждём столько, сколько попросили
                problem = Problem("LibreLinkUp просит подождать", e.message)
            } catch (e: Exception) {
                problem = Problem("Нет связи с LibreLinkUp", e.message)
            }
            problem?.let { Log.w(SyncWorker.TAG, "минутный режим: ${it.title} ${it.text.orEmpty()}") }
            settings = SettingsStore(this).load()   // пока шёл запрос, настройки могли поменять
            post(settings)
            delay(pause)
        }
    }

    /** Сервис останавливается, а уведомление с подсказкой остаётся. */
    private fun finish(message: String) {
        val notification = builder(CHANNEL_LIVE, message, null).setOngoing(false).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun post(settings: AppSettings) {
        if (hidden) return
        val (key, notification) = render(settings)
        if (key == lastPosted) return   // ничего не поменялось — не дёргаем систему
        lastPosted = key
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    /** Уведомление по текущему состоянию и ключ, по которому видно, изменилось ли оно. */
    private fun render(s: AppSettings): Pair<String, Notification> {
        val channel = if (s.liveShowValue) CHANNEL_LIVE else CHANNEL_QUIET
        problem?.let { p ->   // об ошибке говорим и в тихом режиме
            return "problem|$channel|${p.title}|${p.text}" to builder(channel, p.title, p.text).build()
        }
        val r = latest
        if (s.liveShowValue && r != null) {
            val value = formatGlucose(r.mgDl, s.unit)
            val title = listOf(value, s.unit.label, r.arrow).filter { it.isNotEmpty() }.joinToString(" ")
            val time = r.time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            val mood = when (levelOf(r.mgDl, s.lowMgDl, s.highMgDl)) {
                GlucoseLevel.LOW -> "ниже диапазона"
                GlucoseLevel.IN_RANGE -> "в диапазоне ♡"
                GlucoseLevel.HIGH -> "выше диапазона"
            }
            val text = "в $time · $mood"
            return "value|$title|$text" to builder(channel, title, text, value).build()
        }
        val every = if (s.liveMinutes == 1) "каждую минуту" else "раз в ${s.liveMinutes} мин"
        val title = "SugarBridge присматривает за сахаром ✧"
        return "quiet|$channel|$every" to builder(channel, title, "Обновление $every").build()
    }

    private fun builder(channel: String, title: String, text: String?, value: String? = null): Notification.Builder {
        val openApp = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)   // не плодить копии экрана
        val open = PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, action(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val dismissed = PendingIntent.getService(this, 2, action(ACTION_DISMISSED), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel)
            .setSmallIcon(if (value != null) valueIcon(value) else Icon.createWithResource(this, R.drawable.ic_stat_tenshi))
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text) }
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)   // обновлять молча, без звука и вибрации
            .setColor(0xFFF06FA4.toInt())
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
    }

    private fun action(name: String) = Intent(this, LiveSyncService::class.java).setAction(name)

    /** Значение сахара прямо в строке состояния: значок рисуется из текста, система красит его в свой цвет. */
    private fun valueIcon(value: String): Icon {
        val size = 96
        val bitmap = createBitmap(size, size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = size * 0.78f
        }
        val width = paint.measureText(value)
        if (width > size * 0.98f) paint.textSize *= size * 0.98f / width
        val bounds = Rect()
        paint.getTextBounds(value, 0, value.length, bounds)
        Canvas(bitmap).drawText(value, size / 2f, size / 2f - bounds.exactCenterY(), paint)
        return Icon.createWithBitmap(bitmap)
    }

    /** «Не давать телефону засыпать»: процессор не уходит в сон, и пауза между опросами не растягивается. */
    private fun holdWakeLock(s: AppSettings) {
        if (s.liveKeepAwake) {
            val lock = keepAwake ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SugarBridge:live")
                .apply { setReferenceCounted(false) }
                .also { keepAwake = it }
            lock.acquire((s.liveMinutes + 5) * 60_000L)   // продлевается на каждом круге цикла
        } else {
            keepAwake?.let { if (it.isHeld) it.release() }
        }
    }

    /** Без постоянной блокировки сна держим процессор хотя бы на время самого запроса. */
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
        // «live» был у версии из гайда с важностью LOW: у таких уведомлений нет значка в строке состояния,
        // а важность готового канала приложение поменять не может — поэтому новый id
        const val CHANNEL_LIVE = "glucose"
        internal const val LEGACY_CHANNEL = "live"
        const val CHANNEL_QUIET = "quiet"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"
        private const val ACTION_DISMISSED = "dismissed"
        private const val ACTION_RELOAD = "reload"
        private const val MIN_GAP_MS = 55_000L

        fun start(context: Context) = send(context, null)

        /** Применить новые настройки к уже работающему сервису (или запустить его). */
        fun reload(context: Context) = send(context, ACTION_RELOAD)

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveSyncService::class.java))
        }

        private fun send(context: Context, action: String?) {
            try {
                context.startForegroundService(Intent(context, LiveSyncService::class.java).setAction(action))
            } catch (e: Exception) {
                Log.e(SyncWorker.TAG, "не удалось запустить минутный режим", e)
            }
        }
    }
}

/** Каналы уведомлений минутного режима. Создаём и при запуске приложения: без канала нельзя открыть его настройки. */
fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.deleteNotificationChannel(LiveSyncService.LEGACY_CHANNEL)
    // обычная важность — иначе Android не покажет число в строке состояния; звук и вибрацию выключаем
    manager.createNotificationChannel(
        NotificationChannel(LiveSyncService.CHANNEL_LIVE, "Текущий сахар", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Сахар в строке состояния и в шторке в минутном режиме"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
    )
    manager.createNotificationChannel(
        NotificationChannel(LiveSyncService.CHANNEL_QUIET, "Фоновая работа", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Тихое уведомление минутного режима. Его можно выключить — режим продолжит работать"
            setShowBadge(false)
        }
    )
}
