package dev.kettu.hyangsang.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.kettu.hyangsang.HyangsangApplication
import dev.kettu.hyangsang.data.prefs.FeedSyncInterval
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Refreshes enabled feeds while the app isn't open, so articles that drop out of a feed between
// visits are still stored. Also clears old article text, as app start does
class FeedSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HyangsangApplication
        // Each feed records its own failure, and the next run tries again
        app.rssFeedRepository.refreshEnabledFeeds()
        app.articleRepository.clearUnopenedContent(
            app.userPreferencesRepository.contentRetentionFlow.first()
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "feed_sync"

        // Safe to call on every start: an unchanged interval keeps the existing schedule
        fun schedule(context: Context, interval: FeedSyncInterval) {
            val workManager = WorkManager.getInstance(context)
            val hours = interval.hours?.toLong()
            if (hours == null) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<FeedSyncWorker>(hours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                // Feeds were just refreshed by opening the app
                .setInitialDelay(hours, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
