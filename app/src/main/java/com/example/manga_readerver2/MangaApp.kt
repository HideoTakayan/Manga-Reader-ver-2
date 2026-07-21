package com.example.manga_readerver2

import android.app.Application
import com.example.manga_readerver2.core.source.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.get

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import rx.plugins.RxJavaHooks
import logcat.logcat

class MangaApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        val client = Injekt.get<OkHttpClient>()
        val sourceManager = Injekt.get<com.example.manga_readerver2.core.source.SourceManager>()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                add(com.example.manga_readerver2.core.utils.MangaCoverFetcher.Factory(sourceManager, client))
            }
            .build()
    }
    // Sử dụng CoroutineScope tùy chỉnh thay thế cho GlobalScope nhằm quản lý vòng đời chặt chẽ (có thể chủ động hủy khi ứng dụng dừng)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.DEBUG)

        // Cấu hình trình bắt ngoại lệ RxJava chưa xử lý (Undeliverable exceptions) từ các extension để ngăn chặn sự cố sập ứng dụng
        RxJavaHooks.setOnError { e ->
            logcat(LogPriority.ERROR) { "Undeliverable RxJava exception: $e" }
        }

        with(AppModule(this)) {
            Injekt.registerInjectables()
        }

        // Tiền tải các thành phần mở rộng và thực thi dọn dẹp các tệp tạm thời
        applicationScope.launch {
            try {
                // Cơ chế bảo vệ: Dọn dẹp các tệp tin tạm (cache/temp) và thư mục _tmp không còn sử dụng
                cleanupTempFiles()
            } catch (e: Exception) {
                // Log and ignore
            }
        }
        
        // Thiết lập dịch vụ chạy ngầm để cập nhật dữ liệu thư viện
        val libraryPreferences = Injekt.get<com.example.manga_readerver2.core.preference.LibraryPreferences>()
        com.example.manga_readerver2.core.updater.LibraryUpdateJob.setupTask(
            context = this,
            prefInterval = libraryPreferences.updateInterval.get(),
            wifiOnly = libraryPreferences.updateWifiOnly.get()
        )

        val generalPreferences = Injekt.get<com.example.manga_readerver2.core.preference.GeneralPreferences>()
        com.example.manga_readerver2.core.updater.CacheClearJob.setupTask(
            context = this,
            autoClear = generalPreferences.autoClearCache.get()
        )

        // Đăng ký trình quản lý khóa ứng dụng (AppLockManager) vào vòng đời hệ thống
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(com.example.manga_readerver2.core.security.AppLockManager)
    }

    private fun cleanupTempFiles() {
        val fileManager = Injekt.get<com.example.manga_readerver2.core.utils.FileManager>()
        
        // 1. Dọn dẹp bộ nhớ đệm hình ảnh đang đọc (reader_page_*.tmp)
        cacheDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        
        // 2. Dọn dẹp các thư mục nén tạm thời (_tmp) tồn đọng trong thư mục tải xuống
        val downloadDir = fileManager.getDownloadPath()
        if (downloadDir.exists()) {
            downloadDir.listFiles()?.filter { it.isDirectory }?.forEach { sourceDir ->
                sourceDir.listFiles()?.filter { it.isDirectory }?.forEach { mangaDir ->
                    mangaDir.listFiles()?.filter { it.isDirectory && it.name.endsWith("_tmp") }?.forEach { 
                        it.deleteRecursively() 
                    }
                }
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
