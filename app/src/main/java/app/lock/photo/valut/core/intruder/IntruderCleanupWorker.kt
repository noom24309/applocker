package app.lock.photo.valut.core.intruder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lock.photo.valut.domain.repository.IntruderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Runs about once a day to honour the auto-delete setting and the max-records limit.
 * Plain WorkManager + a Hilt [EntryPoint] (no hilt-work dependency needed). Never blocks
 * app startup; enqueued with KEEP so it isn't rescheduled on every launch.
 */
class IntruderCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun intruderRepository(): IntruderRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val repo = EntryPointAccessors
                .fromApplication(applicationContext, WorkerEntryPoint::class.java)
                .intruderRepository()
            repo.autoDeleteOldAttempts()
            repo.enforceMaxRecordLimit()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "intruder_cleanup"

        /** Schedules the daily cleanup (idempotent). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<IntruderCleanupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
