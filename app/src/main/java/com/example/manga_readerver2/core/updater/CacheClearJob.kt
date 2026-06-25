package com.example.manga_readerver2.core.updater

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object CacheClearJob {
    private const val TAG = "CacheClearWorker"

    fun setupTask(context: Context, autoClear: Boolean) {
        val workManager = WorkManager.getInstance(context)

        if (!autoClear) {
            workManager.cancelUniqueWork(TAG)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .build()

        val request = PeriodicWorkRequestBuilder<CacheClearWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
