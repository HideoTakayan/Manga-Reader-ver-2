package com.example.manga_readerver2.core.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoBackupJob {
    private const val TAG = "AutoBackup"

    fun setupTask(context: Context, enabled: Boolean, intervalHours: Int) {
        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            workManager.cancelUniqueWork(TAG)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            intervalHours.toLong(), TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
