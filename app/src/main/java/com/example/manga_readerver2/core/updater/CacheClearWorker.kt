package com.example.manga_readerver2.core.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil3.imageLoader
import com.example.manga_readerver2.core.preference.GeneralPreferences
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class CacheClearWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val generalPreferences: GeneralPreferences = Injekt.get()

    override suspend fun doWork(): Result {
        if (!generalPreferences.autoClearCache.get()) {
            return Result.success()
        }

        try {
            val maxCacheBytes = generalPreferences.maxCacheSize.get() * 1024L * 1024L
            
            // Calculate cache size
            val coilCacheDir = context.cacheDir.resolve("image_cache")
            val webViewCacheDir = context.cacheDir.resolve("WebView")
            
            val totalCacheSize = getFolderSize(coilCacheDir) + getFolderSize(webViewCacheDir)

            if (totalCacheSize > maxCacheBytes) {
                logcat { "Cache size ($totalCacheSize bytes) exceeds max ($maxCacheBytes bytes). Clearing..." }
                
                // Clear Coil cache
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()
                
                // Clear WebView cache (have to delete directory manually since we're in background)
                webViewCacheDir.deleteRecursively()
                
                logcat { "Cache cleared successfully." }
            }

            return Result.success()
        } catch (e: Exception) {
            logcat { "Failed to clear cache: ${e.message}" }
            return Result.failure()
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size = file.length()
        }
        return size
    }
}
