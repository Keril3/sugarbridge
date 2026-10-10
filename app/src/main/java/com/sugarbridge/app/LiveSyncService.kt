package com.sugarbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Сервис переднего плана: раз в минуту синхронизирует и показывает сахар в уведомлении. */
class LiveSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {   // нажали «Остановить» в уведомлении
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Текущий сахар", NotificationManager.IMPORTANCE_LOW)
        )
        try {
            startForeground(NOTIFICATION_ID, notification("Подключаюсь…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            // Android может запретить запуск из фона. Не страшно: останется синхронизация раз в 15 минут
            Log.e("SugarSync", "startForeground не удался", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    var pause = 60_000L   // раз в минуту: чаще LibreLinkUp и сам не обновляется
                    val text = try {
                        syncOnce(this@LiveSyncService).latest?.text() ?: "Замеров пока нет"
                    } catch (e: CancellationException) {
                        throw e   // сервис остановили — выходим из цикла
                    } catch (e: LoginFailed) {
                        Libre.signOut(this@LiveSyncService)   // неверный пароль не повторяем
                        val message = "Пароль не подошёл: откройте приложение и войдите заново"
                        manager.notify(NOTIFICATION_ID, notification(message, ongoing = false))
                        stopForeground(STOP_FOREGROUND_DETACH)   // сервис останавливается, сообщение остаётся
                        stopSelf()
                        return@launch
                    } catch (e: RateLimited) {
                        pause = maxOf(60, e.retryAfter) * 1000L   // ждём столько, сколько попросили
                        "Ошибка: ${e.message}"
                    } catch (e: Exception) {
                        "Ошибка: ${e.message}"
                    }
                    manager.notify(NOTIFICATION_ID, notification(text))
                    delay(pause)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String, ongoing: Boolean = true): Notification {
        val openApp = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)   // не плодить копии экрана
        val open = PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LiveSyncService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(text)
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)   // обновлять молча, без звука и вибрации
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "live"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"
    }
}
