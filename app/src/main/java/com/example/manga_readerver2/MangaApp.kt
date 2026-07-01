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
    // Fix BUG-16: Dùng custom scope thay vì GlobalScope — có thể cancel khi app dừng
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.DEBUG)

        // Catch Undeliverable RxJava exceptions from extensions to prevent app crashes
        RxJavaHooks.setOnError { e ->
            logcat(LogPriority.ERROR) { "Undeliverable RxJava exception: $e" }
        }

        with(AppModule(this)) {
            Injekt.registerInjectables()
        }

        // Pre-load extensions and cleanup temp files
        applicationScope.launch {
            try {
                // Hardening: Dọn dẹp file tạm và thư mục _tmp còn sót lại
                cleanupTempFiles()
            } catch (e: Exception) {
                // Log and ignore
            }
        }
        
        // Setup Background Library Updates
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

        // Register AppLockManager
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(com.example.manga_readerver2.core.security.AppLockManager)
    }

    private fun cleanupTempFiles() {
        val fileManager = Injekt.get<com.example.manga_readerver2.core.utils.FileManager>()
        
        // 1. Dọn dẹp cacheDir (reader_page_*.tmp)
        cacheDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        
        // 2. Dọn dẹp các thư mục _tmp trong thư mục download
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
