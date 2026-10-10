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
        val result = syncOnce(applicationContext)
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
    }

    companion object {
        const val TAG = "SugarSync"
    }
}

fun scheduleSync(context: Context) {
    val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)   // 15 минут — минимум для WorkManager
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    // KEEP: если задача уже запланирована, вторую не создавать
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("sugar-sync", ExistingPeriodicWorkPolicy.KEEP, request)
}
