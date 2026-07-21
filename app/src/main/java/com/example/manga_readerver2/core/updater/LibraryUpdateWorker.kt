package com.example.manga_readerver2.core.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.manga_readerver2.MainActivity
import com.example.manga_readerver2.core.source.SourceManager
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val mangaRepository: MangaRepository = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()

    override suspend fun doWork(): Result {
        logcat { "Starting background library update..." }
        createNotificationChannel()

        try {
            val libraryMangas = mangaRepository.getLibrary().first()
            var newUpdatesCount = 0

            libraryMangas.forEach { libraryManga ->
                val manga = libraryManga.manga
                val source = sourceManager.get(manga.source) ?: return@forEach

                try {
                    val sManga = eu.kanade.tachiyomi.source.model.SManga.create().apply {
                        url = manga.url
                        title = manga.title
                    }
                    val networkChapters = source.getChapterList(sManga)
                    val existingChapters = mangaRepository.getChaptersByMangaId(manga.id)
                    val existingUrls = existingChapters.map { it.url }.toSet()

                    val newChapters = networkChapters.filter { it.url !in existingUrls }
                    
                    if (newChapters.isNotEmpty()) {
                        val chaptersToInsert = newChapters.map { sChapter ->
                            Chapter(
                                id = 0L,
                                mangaId = manga.id,
                                url = sChapter.url,
                                name = sChapter.name,
                                dateUpload = sChapter.date_upload,
                                chapterNumber = sChapter.chapter_number,
                                dateFetch = System.currentTimeMillis()
                            )
                        }
                        mangaRepository.insertChapters(chaptersToInsert)
                        newUpdatesCount += newChapters.size
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Error updating manga ${manga.title}: ${e.message}" }
                }
            }

            if (newUpdatesCount > 0) {
                showUpdateNotification(newUpdatesCount)
            }

            logcat { "Background library update finished. $newUpdatesCount new chapters found." }
            return Result.success()

        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "LibraryUpdateWorker failed: ${e.message}" }
            return Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Cập nhật Thư viện"
            val descriptionText = "Thông báo khi có chương truyện mới"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showUpdateNotification(newUpdatesCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Cập nhật Thư viện")
            .setContentText("Tìm thấy $newUpdatesCount chương truyện mới!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val TAG = "LibraryUpdateWorker"
        private const val CHANNEL_ID = "library_updates_channel"
        private const val NOTIFICATION_ID = 1002  // Đảm bảo định danh duy nhất (khác với DownloadService) để ngăn chặn tình trạng xung đột thông báo (Notification override)
    }
}
