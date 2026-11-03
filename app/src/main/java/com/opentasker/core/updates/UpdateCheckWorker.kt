package com.opentasker.core.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.opentasker.app.BuildConfig
import com.opentasker.core.storage.ScheduledWorkerId
import com.opentasker.core.storage.recordingOutcome
import com.opentasker.core.logging.AppLogger
import java.util.concurrent.TimeUnit

/** The downloadable APK channel is only the standard distribution; stores update their own builds. */
object UpdateCheckAvailability {
    const val STANDARD_DISTRIBUTION = "standard"

    fun isAvailable(): Boolean = BuildConfig.DISTRIBUTION == STANDARD_DISTRIBUTION
}

/** Performs one bounded check against the user's selected branch update channel. */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        recordingOutcome(ScheduledWorkerId.UPDATE_CHECK) { runWork() }

    private suspend fun runWork(): Result {
        if (!UpdateCheckAvailability.isAvailable()) return Result.success()
        val settings = UpdateCheckSettings(applicationContext).load()
        if (!settings.enabled) return Result.success()

        return when (UpdateCheckRunner.checkAndRecord(applicationContext, settings.selectedBranch)) {
            is UpdateCheckRunResult.Completed -> Result.success()
            UpdateCheckRunResult.Failed -> {
                AppLogger.info(TAG, "Scheduled branch release check did not complete")
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "optional_release_update_check"
        private const val INTERVAL_HOURS = 24L

        fun sync(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val settings = UpdateCheckSettings(context).load()
            if (!UpdateCheckAvailability.isAvailable() || !settings.enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueIfEnabled(context: Context) = sync(context)
    }
}
