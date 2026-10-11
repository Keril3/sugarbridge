package com.sugarbridge.app.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.libre.LibreError
import com.sugarbridge.app.libre.LoginFailed
import com.sugarbridge.app.libre.RateLimited
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val result = syncAndRecord(applicationContext)
        Log.i(TAG, "written: ${result.written}")
        Result.success()
    } catch (e: CancellationException) {   // WorkManager stopped the job itself: not an error
        throw e
    } catch (e: LoginFailed) {              // the password didn't work: no retries
        Log.e(TAG, e.message ?: "sign-in failed")
        Libre.signOut(applicationContext)   // the sync stays quiet until you sign in again
        Result.failure()
    } catch (e: RateLimited) {              // LibreLinkUp asks to wait: WorkManager retries later
        Log.w(TAG, e.message ?: "429")
        Result.retry()
    } catch (e: LibreError) {               // e.g. LibreLinkUp has no connections
        Log.e(TAG, e.message ?: "LibreLinkUp error")
        Result.failure()
    } catch (e: IOException) {              // no network or a server failure: retry later
        Log.w(TAG, "network: ${e.message}")
        Result.retry()
    } catch (e: IllegalStateException) {    // no Health Connect permission or nobody signed in
        Log.e(TAG, e.message ?: "error")
        Result.failure()
    } catch (e: Exception) {                // something unexpected, e.g. Health Connect refused
        Log.e(TAG, "sync crashed", e)
        Result.failure()
    }

    companion object {
        const val TAG = "SugarSync"
        const val WORK_NAME = "sugarbridge-sync"

        // The job's name while SyncWorker lived in the com.sugarbridge.app package. WorkManager stores the class
        // name, and that job can't be switched to the class in the new package: it is cancelled and a new one created.
        const val LEGACY_WORK_NAME = "sugar-sync"
    }
}

/** Schedules the background sync from the settings. Safe to call any number of times. */
fun scheduleSync(context: Context, settings: AppSettings = SettingsStore(context).load()) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(SyncWorker.LEGACY_WORK_NAME)
    if (settings.backgroundMinutes <= 0) {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        return
    }
    // 15 minutes is the WorkManager minimum
    val request = PeriodicWorkRequestBuilder<SyncWorker>(settings.backgroundMinutes.toLong(), TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    // UPDATE: an already scheduled job gets the new interval without resetting its timer
    workManager.enqueueUniquePeriodicWork(SyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
}
