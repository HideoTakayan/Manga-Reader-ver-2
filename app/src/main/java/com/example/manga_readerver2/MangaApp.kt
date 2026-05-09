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

class MangaApp : Application() {
    // Fix BUG-16: Dùng custom scope thay vì GlobalScope — có thể cancel khi app dừng
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.DEBUG)

        with(AppModule(this)) {
            Injekt.registerInjectables()
        }

        // Pre-load extensions and cleanup temp files
        applicationScope.launch {
            try {
                Injekt.get<ExtensionManager>().loadLocalExtensions()
                
                // Hardening: Dọn dẹp file tạm và thư mục _tmp còn sót lại
                cleanupTempFiles()
            } catch (e: Exception) {
                // Log and ignore
            }
        }
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
