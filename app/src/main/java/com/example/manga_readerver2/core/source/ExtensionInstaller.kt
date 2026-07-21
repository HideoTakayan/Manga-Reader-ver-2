package com.example.manga_readerver2.core.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlinx.serialization.json.Json
import com.example.manga_readerver2.source_js.model.PluginConfig

class ExtensionInstaller(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val httpClient: OkHttpClient = Injekt.get()
    private val activeJobs = mutableMapOf<String, Job>()

    fun downloadAndInstall(extension: Extension.Available): Flow<InstallStep> {
        val step = MutableStateFlow(InstallStep.Pending)
        
        val job = scope.launch {
            val isZip = extension.apkName.endsWith(".zip", ignoreCase = true)
            val isJsExtension = extension.pkgName.startsWith("js.extension.") && !isZip
            if (isJsExtension) {
                try {
                    step.value = InstallStep.Downloading
                    val api = Injekt.get<ExtensionApi>()
                    val baseUrl = api.getApkUrl(extension).removeSuffix("/")
                    val destDir = File(context.filesDir, "js_extensions/${extension.pkgName}")
                    if (destDir.exists()) destDir.deleteRecursively()
                    destDir.mkdirs()

                    val pluginRequest = Request.Builder().url("$baseUrl/plugin.json").build()
                    val pluginResponse = httpClient.newCall(pluginRequest).execute()
                    if (!pluginResponse.isSuccessful) throw Exception("Failed to load plugin.json")
                    val pluginJsonText = pluginResponse.body?.string() ?: throw Exception("Empty plugin.json")
                    File(destDir, "plugin.json").writeText(pluginJsonText)

                    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
                    val configText = pluginJsonText.replace(Regex(",(?=\\s*[}\\]])"), "")
                    val config = try { json.decodeFromString<PluginConfig>(configText) } catch (e: Exception) { null }

                    val scriptsToDownload = mutableSetOf<String>()
                    
                    if (config != null) {
                        config.script.home?.let { scriptsToDownload.add(it) }
                        config.script.genre?.let { scriptsToDownload.add(it) }
                        config.script.detail?.let { scriptsToDownload.add(it) }
                        config.script.search?.let { scriptsToDownload.add(it) }
                        config.script.page?.let { scriptsToDownload.add(it) }
                        config.script.toc?.let { scriptsToDownload.add(it) }
                        config.script.chap?.let { scriptsToDownload.add(it) }
                    } else {
                        // Kích hoạt phân tích dự phòng qua JSONObject trong trường hợp quá trình parse ban đầu phát sinh ngoại lệ
                        val jsonObject = org.json.JSONObject(pluginJsonText)
                        val scriptObj = jsonObject.optJSONObject("script")
                        if (scriptObj != null) {
                            val keys = scriptObj.keys()
                            while (keys.hasNext()) {
                                scriptsToDownload.add(scriptObj.getString(keys.next()))
                            }
                        }
                    }

                    // Chủ động tiến hành tải các tệp tin lõi (src/) theo chuẩn VBook nếu chưa được tích hợp sẵn
                    val standardScripts = listOf("src/home.js", "src/detail.js", "src/toc.js", "src/chap.js", "src/search.js", "src/genre.js", "src/page.js")
                    scriptsToDownload.addAll(standardScripts)

                    for (scriptName in scriptsToDownload) {
                        val scriptRequest = Request.Builder().url("$baseUrl/$scriptName").build()
                        val scriptResponse = httpClient.newCall(scriptRequest).execute()
                        if (scriptResponse.isSuccessful) {
                            val scriptText = scriptResponse.body?.string() ?: ""
                            val scriptFile = File(destDir, scriptName)
                            scriptFile.parentFile?.mkdirs()
                            scriptFile.writeText(scriptText)
                        }
                    }

                    // Đồng bộ tệp tin biểu tượng (icon) nếu có
                    val iconRequest = Request.Builder().url("$baseUrl/icon.png").build()
                    val iconResponse = httpClient.newCall(iconRequest).execute()
                    if (iconResponse.isSuccessful) {
                        File(destDir, "icon.png").writeBytes(iconResponse.body?.bytes() ?: ByteArray(0))
                    }

                    step.value = InstallStep.Installed
                    // Kích hoạt tiến trình làm mới (refresh) thông qua ExtensionManager đối với định dạng thư mục phân rã
                    Injekt.get<ExtensionManager>().refreshInstalledExtensions()
                } catch (e: Exception) {
                    logcat.logcat("ExtensionInstaller", logcat.LogPriority.ERROR) { "Lỗi tải JS extension: ${e.message}" }
                    step.value = InstallStep.Error
                }
                return@launch
            }

            val extensionStr = if (isZip) ".zip" else ".apk"
            val tmpFile = File(context.cacheDir, "extension_${extension.pkgName}$extensionStr")
            try {
                step.value = InstallStep.Downloading
                val api = Injekt.get<ExtensionApi>()
                val downloadUrl = api.getApkUrl(extension)
                logcat.logcat("ExtensionInstaller", logcat.LogPriority.INFO) {
                    "[INSTALL] Bắt đầu tải: ${extension.pkgName} từ $downloadUrl"
                }
                val request = Request.Builder().url(downloadUrl).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("HTTP ${response.code} khi tải extension từ $downloadUrl")

                response.body?.byteStream()?.use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                logcat.logcat("ExtensionInstaller", logcat.LogPriority.INFO) {
                    "[INSTALL] Đã tải xong: ${tmpFile.absolutePath} (${tmpFile.length()} bytes)"
                }

                if (isZip) {
                    step.value = InstallStep.Installing
                    val destDir = installJsZip(tmpFile, extension.pkgName)
                    logcat.logcat("ExtensionInstaller", logcat.LogPriority.INFO) {
                        "[INSTALL] Đã giải nén ZIP vào: ${destDir.absolutePath}"
                    }
                    // Xác minh sự tồn tại của tệp plugin.json sau khi quá trình giải nén hoàn tất
                    val pluginJson = File(destDir, "plugin.json")
                    if (!pluginJson.exists()) {
                        // Rà soát kiến trúc thư mục con (xử lý tình trạng mã nguồn từ GitHub thường được bao bọc trong một root folder ảo)
                        val subDirs = destDir.listFiles { f -> f.isDirectory }
                        val nested = subDirs?.firstOrNull { File(it, "plugin.json").exists() }
                        if (nested != null) {
                            logcat.logcat("ExtensionInstaller", logcat.LogPriority.WARN) {
                                "[INSTALL] plugin.json nằm trong subfolder: ${nested.name}. Sẽ di chuyển lên thư mục gốc."
                            }
                            // Dịch chuyển toàn bộ cấu trúc thư mục con lên cấp thư mục đích (destDir)
                            nested.listFiles()?.forEach { file ->
                                file.copyRecursively(File(destDir, file.name), overwrite = true)
                            }
                            nested.deleteRecursively()
                        } else {
                            val files = destDir.listFiles()?.joinToString { it.name } ?: "(trống)"
                            throw Exception("plugin.json không tìm thấy sau khi giải nén. Nội dung thư mục: $files")
                        }
                    }
                    logcat.logcat("ExtensionInstaller", logcat.LogPriority.INFO) {
                        val contents = destDir.walkTopDown().filter { it.isFile }.joinToString(", ") { it.relativeTo(destDir).path }
                        "[INSTALL] Nội dung sau cài: $contents"
                    }
                    step.value = InstallStep.Installed
                    Injekt.get<ExtensionManager>().refreshInstalledExtensions()
                } else {
                    step.value = InstallStep.Installing
                    installApk(tmpFile)
                    step.value = InstallStep.SystemInstallStarted
                }
            } catch (e: Exception) {
                logcat.logcat("ExtensionInstaller", logcat.LogPriority.ERROR) {
                    "[INSTALL] LỖI cài ${extension.pkgName}: ${e.message}\n${e.stackTraceToString()}"
                }
                tmpFile.delete()
                step.value = InstallStep.Error
            }
        }

        activeJobs[extension.pkgName] = job

        return step.asStateFlow()
            .onCompletion {
                activeJobs.remove(extension.pkgName)
            }
    }

    private fun installJsZip(zipFile: File, pkgName: String): File {
        val destDir = File(context.filesDir, "js_extensions/$pkgName")
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()
        logcat.logcat("ExtensionInstaller", logcat.LogPriority.INFO) {
            "[ZIP] Giải nén ${zipFile.name} (${zipFile.length()} bytes) -> ${destDir.absolutePath}"
        }

        try {
            var entryCount = 0
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    // Xử lý lược bỏ thư mục gốc dư thừa (Ví dụ: Định dạng nén từ GitHub thường có dạng "repository-main/plugin.json")
                    val entryName = entry.name.let { name ->
                        val parts = name.split("/")
                        if (parts.size > 1 && parts[0].isNotEmpty() && !name.startsWith("src/") && !name.startsWith("plugin")) {
                            // Phân tích và xác định liệu thành phần đầu tiên của đường dẫn có phải là thư mục gốc dư thừa hay không
                            parts.drop(1).joinToString("/")
                        } else {
                            name
                        }
                    }.trimStart('/')
                    
                    if (entryName.isEmpty()) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    
                    val file = File(destDir, entryName)
                    logcat.logcat("ExtensionInstaller", logcat.LogPriority.INFO) {
                        "[ZIP] Entry: ${entry.name} -> $entryName"
                    }
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            logcat.logcat("ExtensionInstaller", logcat.LogPriority.INFO) {
                "[ZIP] Giải nén xong: $entryCount entries"
            }
        } catch (e: Exception) {
            logcat.logcat("ExtensionInstaller", logcat.LogPriority.ERROR) {
                "[ZIP] Lỗi giải nén: ${e.message}"
            }
            throw e
        } finally {
            zipFile.delete()
        }
        return destDir
    }

    private fun installApk(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    fun uninstallApk(pkgName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
