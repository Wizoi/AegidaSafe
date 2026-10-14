package com.aegida.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aegida.alerts.DailyCheck
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Runs the daily check. It has no network constraint on purpose: the check must run offline, and a failed
 * feed update only makes the lists older (HealthDetector says so after 3 days). The 9 MB feed is only downloaded
 * on an unmetered network (Wi-Fi), unless the lists are already more than 3 days old (DailyCheck decides that).
 */
@HiltWorker
class DailyCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dailyCheck: DailyCheck,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        dailyCheck.run(downloadFeed = onUnmeteredNetwork())
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (ignored: Exception) {
        // Any failure is handled the same way: WorkManager is asked to retry, then to give up. The reason is not shown to anyone.
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    /** False when the network is metered, unknown or unreadable: then DailyCheck skips the download while the lists are fresh. */
    private fun onUnmeteredNetwork(): Boolean = try {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    } catch (ignored: SecurityException) {
        // Not allowed to read the network state: treated as "not on Wi-Fi", which only delays the big download.
        false
    }

    companion object {
        private const val NAME = "daily-check"
        private const val MAX_ATTEMPTS = 3
        private const val REPEAT_HOURS = 24L
        private const val FLEX_HOURS = 6L

        /** Safe to call on every start: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(REPEAT_HOURS, TimeUnit.HOURS, FLEX_HOURS, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
