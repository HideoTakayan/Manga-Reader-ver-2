package com.example.manga_readerver2.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.manga_readerver2.R
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import android.os.PowerManager
import android.app.PendingIntent
import com.example.manga_readerver2.core.utils.DiskUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import eu.kanade.tachiyomi.source.online.HttpSource
import com.example.manga_readerver2.source_js.JsSource
import com.example.manga_readerver2.core.utils.ZipUtil
import com.example.manga_readerver2.core.utils.FileManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import com.example.manga_readerver2.core.utils.EpubExporter

class DownloadService : Service() {

    private val downloadManager: DownloadManager = Injekt.get()
    private val fileManager: FileManager = Injekt.get()
    private val okHttpClient: OkHttpClient = Injekt.get()
    private val downloadCache: com.example.manga_readerver2.core.download.DownloadCache = Injekt.get()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mutex = kotlinx.coroutines.sync.Mutex()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Đang chuẩn bị tải..."))
        
        // Chỉ theo dõi để dừng service khi queue rỗng.
        // processQueue() được drive bởi finally của downloadJob để tránh race condition.
        downloadManager.queueState.onEach { queue ->
            if (queue.isEmpty()) {
                releaseWakeLock()
                stopSelf()
            }
        }.launchIn(scope)

        // Kick off processing lần đầu
        processQueue(downloadManager.queueState.value)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MRv2:DownloadWakeLock").apply {
            acquire(10 * 60 * 1000L /*10 minutes*/)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun processQueue(queue: List<Download>) {
        scope.launch {
            mutex.withLock {
                val currentDownload = queue.firstOrNull {
                    it.status == Download.State.QUEUE || it.status == Download.State.DOWNLOADING
                }

                if (currentDownload == null) {
                    releaseWakeLock()
                    stopSelf()
                    return@withLock
                }

                val downloadPreferences = Injekt.get<com.example.manga_readerver2.core.preference.DownloadPreferences>()
                if (downloadPreferences.downloadOnlyOverWifi.get()) {
                    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    val isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                    
                    if (!isWifi) {
                        downloadManager.updateDownloadState(currentDownload, Download.State.ERROR)
                        updateNotification("Lỗi tải: Yêu cầu Wi-Fi")
                        
                        // Stop service since we can't download anything on mobile data
                        releaseWakeLock()
                        stopSelf()
                        return@withLock
                    }
                }

                // Guard: job đang chạy thì không start thêm (double check)
                if (downloadJob?.isActive == true) return@withLock

                downloadJob = scope.launch {
                    downloadManager.updateDownloadState(currentDownload, Download.State.DOWNLOADING)
                    updateNotification("Đang tải: ${currentDownload.manga.title} - ${currentDownload.chapter.name}")

                    try {
                        downloadChapter(currentDownload)
                        downloadManager.updateDownloadState(currentDownload, Download.State.DOWNLOADED)
                        downloadManager.removeFromQueue(currentDownload)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        downloadManager.updateDownloadState(currentDownload, Download.State.ERROR)
                    } finally {
                        downloadJob = null
                        val nextQueue = downloadManager.queueState.value
                        if (nextQueue.any { it.status == Download.State.QUEUE }) {
                            processQueue(nextQueue)
                        } else {
                            releaseWakeLock()
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadChapter(download: Download) {
        val source = download.source
        
        // 1. Lấy danh sách trang (có retry)
        val sChapter = eu.kanade.tachiyomi.source.model.SChapter.create().apply {
            url = download.chapter.url
            name = download.chapter.name
        }
        
        var pages: List<eu.kanade.tachiyomi.source.model.Page> = emptyList()
        var retryCount = 0
        while (pages.isEmpty() && retryCount < 3) {
            try {
                pages = source.getPageList(sChapter)
            } catch (e: Exception) {
                retryCount++
                if (retryCount >= 3) throw e
                delay(2000L * retryCount)
            }
        }
        if (pages.isEmpty()) throw Exception("Danh sách trang rỗng sau 3 lần thử")
        
        download.pages = pages
        download.progress = 0

        // Download cover image if not exists
        try {
            val mangaDir = fileManager.getDownloadPath().resolve(download.source.name).resolve(download.manga.title + "_" + download.manga.id)
            if (!mangaDir.exists()) mangaDir.mkdirs()
            val coverFile = File(mangaDir, "cover.jpg")
            
            if (!coverFile.exists() && !download.manga.thumbnailUrl.isNullOrEmpty()) {
                val imageUrl = download.manga.thumbnailUrl!!
                val httpSource = source as? HttpSource
                val requestHeaders = httpSource?.headers ?: okhttp3.Headers.Builder()
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                val request = okhttp3.Request.Builder().url(imageUrl).headers(requestHeaders).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            FileOutputStream(coverFile).use { out -> body.byteStream().copyTo(out) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace() // Lỗi cover không quan trọng, bỏ qua
        }

        if (download.isNovel) {
            // Tải TRUYỆN CHỮ
            val paragraphs = mutableListOf<String>()
            pages.forEach { page ->
                val content = page.imageUrl ?: (source as? HttpSource)?.getImageUrl(page) ?: ""
                paragraphs.add(content.removePrefix("vbook-text://"))
            }
            val novelFile = fileManager.getChapterNovelPath(download.source.name, download.manga.title, download.manga.id.toString(), download.chapter.name)
            val tmpNovelFile = File(novelFile.absolutePath + ".tmp")
            
            val success = EpubExporter.export(download.manga, download.chapter, paragraphs, tmpNovelFile)
            if (success) {
                if (novelFile.exists()) novelFile.delete()
                if (tmpNovelFile.renameTo(novelFile)) {
                    downloadCache.addChapter(download.manga.id, download.chapter.name)
                    download.progress = 100
                    downloadManager.triggerUpdate()
                } else {
                    tmpNovelFile.delete()
                    throw Exception("Lỗi đổi tên file EPUB")
                }
            } else {
                tmpNovelFile.delete()
                throw Exception("Lỗi lưu truyện chữ EPUB")
            }
        } else {
            // Tải TRUYỆN TRANH (Manga) - PHIÊN BẢN 2.0 (Song song)
            val httpSource = source as? HttpSource
            val jsSource = source as? JsSource
            
            if (httpSource == null && jsSource == null) {
                throw Exception("Nguồn không hỗ trợ tải ảnh trực tiếp")
            }
            
            // 0. Kiểm tra bộ nhớ trống (Cần ít nhất 100MB)
            val available = DiskUtil.getAvailableInternalMemorySize()
            if (available < 100 * 1024 * 1024) {
                throw Exception("Bộ nhớ thiết bị không đủ (Cần tối thiểu 100MB)")
            }

            // 2. Tạo thư mục tạm (Chuẩn Mihon: Tải vào _tmp trước khi nén)
            val tmpDir = fileManager.getChapterPath(download.source.name, download.manga.title, download.manga.id.toString(), download.chapter.name + "_tmp")
            if (!tmpDir.exists()) tmpDir.mkdirs()
            
            // Tạo file .nomedia để bảo vệ Gallery khỏi ảnh rác
            File(tmpDir, ".nomedia").createNewFile()
            
            try {
                var downloadedCount = 0
                val parallelLimit = 3 // Tải 3 trang cùng lúc
                
                // 3. Tải song song dùng Flow
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                pages.asFlow().flatMapMerge(concurrency = parallelLimit) { page ->
                    flow {
                        val index = pages.indexOf(page)
                        val digitCount = pages.size.toString().length.coerceAtLeast(3)
                        val filename = "%0${digitCount}d".format(Locale.ENGLISH, index + 1)
                        
                        var success = false
                        for (attempt in 1..3) {
                            try {
                                val imageUrl = page.imageUrl ?: httpSource?.getImageUrl(page) ?: ""
                                if (imageUrl.isEmpty()) throw Exception("Không thể lấy URL ảnh cho trang ${index + 1}")

                                // Fix D2: JsSource không có headers, dùng header default hợp lý hoặc header từ httpSource
                                val requestHeaders = httpSource?.headers ?: okhttp3.Headers.Builder()
                                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                    .build()
                                
                                val request = okhttp3.Request.Builder()
                                    .url(imageUrl)
                                    .headers(requestHeaders)
                                    .build()
                                
                                okHttpClient.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                                    val body = response.body ?: throw Exception("Body rỗng")
                                    
                                    val extension = response.header("Content-Type")?.substringAfter("image/")?.substringBefore(";") ?: "jpg"
                                    val imageFile = File(tmpDir, "$filename.$extension")
                                    
                                    // Bỏ qua nếu file đã tồn tại và không rỗng (Hỗ trợ Resume)
                                    if (imageFile.exists() && imageFile.length() > 0) {
                                        success = true
                                    } else {
                                        FileOutputStream(imageFile).use { out -> body.byteStream().copyTo(out) }
                                        success = true
                                    }
                                }
                                if (success) break
                            } catch (e: Exception) {
                                if (attempt == 3) throw e
                                // Exponential backoff: 2s, 4s, 8s
                                delay((2000L shl (attempt - 1)))
                            }
                        }
                        emit(index)
                    }.flowOn(Dispatchers.IO)
                }.collect { 
                    downloadedCount++
                    download.progress = (downloadedCount * 100) / pages.size
                    downloadManager.triggerUpdate()
                    updateNotification(
                        "Đang tải: ${download.chapter.name}",
                        progress = download.progress,
                        subText = "$downloadedCount / ${pages.size}"
                    )
                }
                
                // 4. Sinh Metadata ComicInfo.xml
                ComicInfo.createComicInfoFile(tmpDir, download.manga, download.chapter, download.source.name)
                
                // 5. Kiểm tra và nén thành CBZ (Mihon Style: Dùng file .tmp khi đang nén)
                val cbzFile = fileManager.getChapterCbzPath(download.source.name, download.manga.title, download.manga.id.toString(), download.chapter.name)
                val tmpCbzFile = File(cbzFile.parent, cbzFile.name + ".tmp")
                
                downloadManager.updateDownloadState(download, Download.State.COMPRESSING)
                updateNotification("Đang nén file CBZ: ${download.chapter.name}")
                
                val zipSuccess = withContext(Dispatchers.IO) {
                    val success = ZipUtil.zipDirectory(tmpDir, tmpCbzFile, deleteSource = true)
                    if (success) {
                        if (cbzFile.exists()) cbzFile.delete()
                        if (tmpCbzFile.renameTo(cbzFile)) {
                            success
                        } else {
                            tmpCbzFile.delete()
                            false
                        }
                    } else {
                        tmpDir.deleteRecursively()
                        tmpCbzFile.delete()
                        false
                    }
                }
                
                if (!zipSuccess) throw Exception("Lỗi khi nén hoặc lưu file CBZ")
                
                // Cập nhật bộ nhớ đệm
                downloadCache.addChapter(download.manga.id, download.chapter.name)
                
                // Tải xong hoàn toàn
                updateNotification("Đã tải xong: ${download.chapter.name}", progress = 100)
            } catch (e: Exception) {
                // Fix D3: Xóa tmpDir nếu có lỗi tải
                if (tmpDir.exists()) tmpDir.deleteRecursively()
                throw e
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            downloadManager.clearQueue()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tải xuống Truyện",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị tiến trình tải truyện"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, progress: Int = -1, subText: String? = null): Notification {
        val stopIntent = Intent(this, DownloadService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang tải truyện")
            .setContentText(text)
            .setSubText(subText)
            .setSmallIcon(R.drawable.app_icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (progress == -1) 0 else progress, progress == -1)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hủy", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String, progress: Int = -1, subText: String? = null) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(text, progress, subText))
    }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "action_stop"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
