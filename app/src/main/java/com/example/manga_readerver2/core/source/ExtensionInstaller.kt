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
                        // Fallback using JSONObject if parsing failed for some reason
                        val jsonObject = org.json.JSONObject(pluginJsonText)
                        val scriptObj = jsonObject.optJSONObject("script")
                        if (scriptObj != null) {
                            val keys = scriptObj.keys()
                            while (keys.hasNext()) {
                                scriptsToDownload.add(scriptObj.getString(keys.next()))
                            }
                        }
                    }

                    // Always attempt to download standard VBook src/ files if not explicitly added
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

                    // Download icon if any
                    val iconRequest = Request.Builder().url("$baseUrl/icon.png").build()
                    val iconResponse = httpClient.newCall(iconRequest).execute()
                    if (iconResponse.isSuccessful) {
                        File(destDir, "icon.png").writeBytes(iconResponse.body?.bytes() ?: ByteArray(0))
                    }

                    step.value = InstallStep.Installed
                    // Trigger refresh via ExtensionManager (since it's not a ZIP anymore)
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
                val request = Request.Builder().url(api.getApkUrl(extension)).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("HTTP ${response.code} khi tải extension")
                
                response.body?.byteStream()?.use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (isZip) {
                    step.value = InstallStep.Installing
                    installJsZip(tmpFile, extension.pkgName)
                    step.value = InstallStep.Installed
                    Injekt.get<ExtensionManager>().refreshInstalledExtensions()
                } else {
                    step.value = InstallStep.Installing
                    installApk(tmpFile)
                    step.value = InstallStep.SystemInstallStarted
                }
            } catch (e: Exception) {
                logcat.logcat("ExtensionInstaller", logcat.LogPriority.ERROR) { "Lỗi cài extension: ${e.message}" }
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

    private fun installJsZip(zipFile: File, pkgName: String) {
        val destDir = File(context.filesDir, "js_extensions/$pkgName")
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        try {
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(destDir, entry.name)
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
        } finally {
            zipFile.delete()
        }
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
