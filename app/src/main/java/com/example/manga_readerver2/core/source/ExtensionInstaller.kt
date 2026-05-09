package com.example.manga_readerver2.core.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.manga_readerver2.BuildConfig
import com.example.manga_readerver2.core.utils.GET
import com.example.manga_readerver2.core.utils.awaitSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileOutputStream

enum class InstallStep {
    Pending,
    Downloading,
    Installing,
    SystemInstallStarted, // Mới: Đã mở trình cài đặt hệ thống
    Installed,
    Error
}

/**
 * Bộ cài đặt Extension: hỗ trợ tải và cài đặt APK (Manga) và JS (Novel).
 */
class ExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
    private val sourcePreferences: SourcePreferences
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val activeSteps = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<InstallStep>>()

    init {
        cleanUp()
    }

    private fun cleanUp() {
        scope.launch {
            try {
                // Xóa các file rác trong cache
                context.cacheDir.listFiles()?.filter { it.name.startsWith("ext_") }?.forEach { 
                    it.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadAndInstall(extension: Extension.Available): Flow<InstallStep> {
        val pkgName = extension.pkgName
        cancelInstall(pkgName)

        val step = MutableStateFlow(InstallStep.Pending)
        activeSteps[pkgName] = step

        val job = scope.launch {
            try {
                step.value = InstallStep.Downloading
                val isZip = extension.apkName.endsWith(".zip") || extension.isVBook
                val isJs = extension.apkName.endsWith(".js") && !extension.isVBook

                val url = when {
                    // VBook: repoUrl = URL đầy đủ của file zip, dùng trực tiếp
                    extension.isVBook -> extension.repoUrl
                    // JS standalone
                    isJs -> "${extension.repoUrl}/scripts/${extension.apkName}"
                    // Mihon APK
                    else -> "${extension.repoUrl}/apk/${extension.apkName}"
                }

                logcat(LogPriority.DEBUG) { "Đang tải extension từ: $url" }
                val response = client.newCall(GET(url)).awaitSuccess()
                val extensionExt = if (isJs) "js" else if (isZip) "zip" else "apk"
                val tmpFile = File(context.cacheDir, "ext_$pkgName.$extensionExt")
                
                response.body?.byteStream()?.use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                step.value = InstallStep.Installing
                when {
                    isZip -> {
                        installZipExtension(extension, tmpFile)
                        // Download icon sau khi cài ZIP (VBook ZIP không có sẵn icon trong app)
                        downloadIconForExtension(extension)
                        step.value = InstallStep.Installed
                    }
                    isJs -> {
                        installJsExtension(extension, tmpFile)
                        step.value = InstallStep.Installed
                    }
                    else -> {
                        if (sourcePreferences.extensionInstaller.get() == 1) {
                            logcat(LogPriority.DEBUG) { "Cài đặt APK nội bộ cho $pkgName" }
                            installInternalApk(pkgName, tmpFile)
                            step.value = InstallStep.Installed
                        } else {
                            logcat(LogPriority.DEBUG) { "Mở trình cài đặt hệ thống cho $pkgName" }
                            installApkExtension(tmpFile)
                            step.value = InstallStep.SystemInstallStarted
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Lỗi cài đặt extension $pkgName: ${e.message}" }
                step.value = InstallStep.Error
            }
        }

        activeJobs[pkgName] = job
        return step.asStateFlow().onCompletion {
            activeJobs.remove(pkgName)
            activeSteps.remove(pkgName)
        }
    }

    private fun installInternalApk(pkgName: String, file: File) {
        val privateExtDir = File(context.filesDir, "exts")
        if (!privateExtDir.exists()) privateExtDir.mkdirs()
        
        val targetFile = File(privateExtDir, "$pkgName.apk")
        file.copyTo(targetFile, overwrite = true)
        file.delete()
    }

    private fun installJsExtension(extension: Extension.Available, file: File) {
        // Fix: dùng substringAfter("vbook.") thay vì substringAfterLast(".")
        // Tránh bug: "vbook.v1.source" -> pluginId = "source" thay vì "v1.source"
        val pluginId = if (extension.pkgName.startsWith("vbook.")) {
            extension.pkgName.substringAfter("vbook.")
        } else {
            extension.pkgName.substringAfterLast(".")
        }
        val pluginDir = File(context.filesDir, "extensions/js/$pluginId")
        val srcDir = File(pluginDir, "src")
        if (!srcDir.exists()) srcDir.mkdirs()

        val scriptName = extension.apkName.substringAfterLast("/")
        file.copyTo(File(srcDir, scriptName), overwrite = true)

        // Tạo plugin.json để JsLoader nhận diện
        val pluginJson = """
            {
                "metadata": {
                    "name": "${extension.name}",
                    "author": "${extension.author ?: "MangaReader"}",
                    "version": "${extension.versionName}",
                    "language": "${extension.lang}",
                    "locale": "${extension.lang}"
                },
                "script": {
                    "home": "$scriptName",
                    "search": "$scriptName",
                    "detail": "$scriptName",
                    "toc": "$scriptName",
                    "chap": "$scriptName"
                }
            }
        """.trimIndent()

        File(pluginDir, "plugin.json").writeText(pluginJson)

        // Tải icon nếu có
        if (extension.iconUrl.isNotEmpty()) {
            try {
                val iconResponse = client.newCall(com.example.manga_readerver2.core.utils.GET(extension.iconUrl)).execute()
                if (iconResponse.isSuccessful) {
                    iconResponse.body?.bytes()?.let { bytes ->
                        File(pluginDir, "icon.png").writeBytes(bytes)
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Không tải được icon cho ${extension.name}: ${e.message}" }
            }
        }

        file.delete()
    }

    private fun installZipExtension(extension: Extension.Available, file: File) {
        // Fix BUG-09: Fallback an toàn khi pkgName không có prefix "vbook."
        val pluginId = when {
            extension.pkgName.startsWith("vbook.") -> extension.pkgName.substringAfter("vbook.")
            else -> extension.pkgName
        }
        val pluginDir = File(context.filesDir, "extensions/js/$pluginId")
        if (pluginDir.exists()) pluginDir.deleteRecursively()
        pluginDir.mkdirs()

        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()

                // Nhận dạng cấu trúc ZIP: có thể có 1 thư mục gốc (bachngocsach/) hoặc không
                val firstLevelPaths = entries
                    .filter { !it.isDirectory }
                    .map { it.name.substringBefore("/") }
                    .distinct()
                val hasSingleRoot = firstLevelPaths.size == 1 && entries.any { it.name.contains("/") }
                val rootFolder = if (hasSingleRoot) firstLevelPaths.first() else null

                entries.forEach { entry ->
                    val entryName = if (hasSingleRoot && rootFolder != null && entry.name.startsWith("$rootFolder/")) {
                        entry.name.substringAfter("$rootFolder/")
                    } else {
                        entry.name
                    }

                    if (entryName.isEmpty()) return@forEach

                    val entryFile = File(pluginDir, entryName)
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            entryFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            logcat(LogPriority.DEBUG) { "Đã cài đặt thành công ZIP extension: $pluginId" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Lỗi giải nén extension $pluginId: ${e.message}" }
            throw e
        } finally {
            file.delete()
        }
    }

    private fun downloadIconForExtension(extension: Extension.Available) {
        if (extension.iconUrl.isEmpty()) return
        val pluginId = extension.pkgName.substringAfter("vbook.")
        val pluginDir = File(context.filesDir, "extensions/js/$pluginId")
        val iconFile = File(pluginDir, "icon.png")
        if (iconFile.exists()) return  // Đã có trong ZIP, không cần tải lại

        try {
            val iconResponse = client.newCall(GET(extension.iconUrl)).execute()
            if (iconResponse.isSuccessful) {
                iconResponse.body?.bytes()?.let { bytes ->
                    iconFile.writeBytes(bytes)
                    logcat(LogPriority.DEBUG) { "Tải icon thành công: ${extension.name}" }
                }
            }
            iconResponse.close()
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Không tải được icon cho ${extension.name}: ${e.message}" }
        }
    }

    private fun installApkExtension(file: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun cancelInstall(pkgName: String) {
        activeJobs.remove(pkgName)?.cancel()
        activeSteps.remove(pkgName)
    }
}
