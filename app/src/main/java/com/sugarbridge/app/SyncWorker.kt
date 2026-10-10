package com.sugarbridge.app

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val result = syncAndRecord(applicationContext)
        Log.i(TAG, "записано: ${result.written}")
        Result.success()
    } catch (e: CancellationException) {   // WorkManager сам остановил задачу — это не ошибка
        throw e
    } catch (e: LoginFailed) {              // пароль не подошёл: повторять нельзя
        Log.e(TAG, e.message ?: "вход не удался")
        Libre.signOut(applicationContext)   // дальше синхронизация молчит, пока вы не войдёте заново
        Result.failure()
    } catch (e: RateLimited) {              // LibreLinkUp просит подождать — WorkManager повторит позже
        Log.w(TAG, e.message ?: "429")
        Result.retry()
    } catch (e: LibreError) {               // например, в LibreLinkUp нет подключений
        Log.e(TAG, e.message ?: "ошибка LibreLinkUp")
        Result.failure()
    } catch (e: IOException) {              // нет сети или сбой сервера — повторим позже
        Log.w(TAG, "сеть: ${e.message}")
        Result.retry()
    } catch (e: IllegalStateException) {    // нет разрешения Health Connect или не выполнен вход
        Log.e(TAG, e.message ?: "ошибка")
        Result.failure()
    } catch (e: Exception) {                // что-то неожиданное, например отказ Health Connect
        Log.e(TAG, "синхронизация упала", e)
        Result.failure()
    }

    companion object {
        const val TAG = "SugarSync"
        const val WORK_NAME = "sugar-sync"
    }
}

/** Планирует фоновую синхронизацию по настройкам. Безопасно вызывать сколько угодно раз. */
fun scheduleSync(context: Context, settings: AppSettings = SettingsStore(context).load()) {
    val workManager = WorkManager.getInstance(context)
    if (settings.backgroundMinutes <= 0) {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        return
    }
    // 15 минут — минимум для WorkManager
    val request = PeriodicWorkRequestBuilder<SyncWorker>(settings.backgroundMinutes.toLong(), TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    // UPDATE: уже запланированная задача получает новый интервал, отсчёт времени не сбрасывается
    workManager.enqueueUniquePeriodicWork(SyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
}
