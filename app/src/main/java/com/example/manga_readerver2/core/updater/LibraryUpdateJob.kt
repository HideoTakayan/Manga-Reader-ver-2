package com.example.manga_readerver2.core.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import logcat.logcat

object LibraryUpdateJob {

    fun setupTask(context: Context, prefInterval: Int, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)

        if (prefInterval == 0) {
            logcat { "Background library update is disabled." }
            workManager.cancelUniqueWork(LibraryUpdateWorker.TAG)
            return
        }

        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(
            prefInterval.toLong(), TimeUnit.HOURS,
            15, TimeUnit.MINUTES // Cấu hình Flex Interval (thời gian tối đa chờ đợi để đồng bộ chu kỳ)
        )
            .addTag(LibraryUpdateWorker.TAG)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            LibraryUpdateWorker.TAG,
            ExistingPeriodicWorkPolicy.UPDATE, // Kích hoạt chính sách UPDATE thay thế cho KEEP/REPLACE nhằm tương thích chuẩn WorkManager 2.9.0+
            request
        )
        
        logcat { "Enqueued background library update every $prefInterval hours. WiFi Only: $wifiOnly" }
    }
}
