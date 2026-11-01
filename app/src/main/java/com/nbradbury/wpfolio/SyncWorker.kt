package com.nbradbury.wpfolio

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nbradbury.wpfolio.data.SessionExpiredException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Refreshes subscriptions, likes and accent colours while the app isn't open, so the feed is
 * current on launch and opening Liked doesn't wait on a large fetch.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        try {
            container.repository.refresh()
        } catch (e: SessionExpiredException) {
            // Signed out now; there's nothing to sync until the user signs in again.
            return Result.success()
        }
        // After the refresh, so a session that just expired can't merge likes back in after
        // sign-out. Best effort: the Liked screen syncs again when opened.
        runCatching { container.repository.syncLiked() }
        // Sampling runs in the app's scope, which can be killed once this returns.
        container.accentSampler.sampleNow()
        return Result.success()
    }

    companion object {
        private const val NAME = "sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints(requiredNetworkType = NetworkType.CONNECTED, requiresBatteryNotLow = true)
                )
                .build()
            // UPDATE applies changes to the request without restarting the schedule on every launch.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
