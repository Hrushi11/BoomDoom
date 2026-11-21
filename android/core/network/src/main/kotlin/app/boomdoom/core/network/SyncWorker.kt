package app.boomdoom.core.network

import android.content.Context
import android.net.ConnectivityManager
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * One worker for the POC: profile -> topics -> feed -> outbox, then video downloads on unmetered networks.
 * While any topic is still being prepared, it re-runs every minute so new lessons appear without push.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val sync: LessonSync,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val result = runCatching { sync.sync() }.getOrElse { return if (runAttemptCount < 5) Result.retry() else Result.failure() }
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        if (!cm.isActiveNetworkMetered) runCatching { sync.downloadPending() }
        if (result.anyPreparing) LessonSyncScheduler.pollSoon(applicationContext)
        return Result.success()
    }
}

object LessonSyncScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun request(delaySeconds: Long) = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(online)
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    /** User-triggered (app start, profile change, pull to refresh). */
    fun syncNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork("lesson-sync", ExistingWorkPolicy.REPLACE, request(0))
    }

    /** Poll while topics are preparing. Separate name so it never cancels a running sync; APPEND_OR_REPLACE
     *  so a poll scheduled from inside the running poll chains after it instead of being dropped. */
    fun pollSoon(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork("lesson-sync-poll", ExistingWorkPolicy.APPEND_OR_REPLACE, request(60))
    }

    fun schedulePeriodic(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("lesson-sync-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
