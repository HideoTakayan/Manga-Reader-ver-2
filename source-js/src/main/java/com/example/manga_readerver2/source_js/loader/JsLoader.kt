package com.example.manga_readerver2.source_js.loader

import android.content.Context
import eu.kanade.tachiyomi.source.Source
import com.example.manga_readerver2.source_js.JsSource
import com.example.manga_readerver2.source_js.engine.VBookEngine
import com.example.manga_readerver2.source_js.model.PluginConfig
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import java.io.File

/**
 * Handles loading of JS-based extensions (VBook style).
 */
object JsLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        // Cần thiết: VBook plugin.json dùng "version": 14 (Int) nhưng model dự kiến String
        coerceInputValues = true
        isLenient = true
    }

    fun loadExtension(context: Context, pluginDir: File, client: OkHttpClient): JsExtensionInfo? {
        val pluginJsonFile = File(pluginDir, "plugin.json")
        if (!pluginJsonFile.exists()) {
            logcat(LogPriority.WARN) { "plugin.json not found in ${pluginDir.absolutePath}" }
            return null
        }

        return try {
            val rawText = pluginJsonFile.readText()
            // Fix: kotlinx.serialization crashes on trailing commas. Clean them up manually.
            val configText = rawText.replace(Regex(",(?=\\s*[}\\]])"), "")
            val config = json.decodeFromString<PluginConfig>(configText)
            
            val scripts = mutableMapOf<String, String>()
            val srcDir = File(pluginDir, "src")
            
            // Load all JS files in srcDir to make them available for dynamic execution
            if (srcDir.exists() && srcDir.isDirectory) {
                srcDir.listFiles { file -> file.extension == "js" }?.forEach { file ->
                    scripts[file.nameWithoutExtension] = file.readText()
                }
            }
            
            // Also load explicitly defined scripts in root just in case
            fun readScript(name: String?, key: String) {
                if (name == null) return
                val rootFile = File(pluginDir, name)
                if (rootFile.exists() && !scripts.containsKey(key)) {
                    scripts[key] = rootFile.readText()
                } else if (!scripts.containsKey(key)) {
                    val srcFile = File(srcDir, name)
                    if (srcFile.exists()) scripts[key] = srcFile.readText()
                }
            }

            readScript(config.script.home, "home")
            readScript(config.script.genre, "genre")
            readScript(config.script.detail, "detail")
            readScript(config.script.search, "search")
            readScript(config.script.page, "page")
            readScript(config.script.toc, "toc")
            readScript(config.script.chap, "chap")

            fun normalizeLang(code: String?): String {
                if (code.isNullOrBlank()) return "all"
                val normalized = code.trim().lowercase()
                return when {
                    normalized == "global" -> "all"
                    normalized.contains("_") -> normalized.substringBefore("_")
                    normalized.contains("-") -> normalized.substringBefore("-")
                    else -> normalized
                }
            }

            // Ưu tiên locale, fallback language, cuối cùng là all.
            val lang = normalizeLang(config.metadata.locale ?: config.metadata.language)

            // Fix: ID phải khớp với ExtensionApi.sourceId formula
            // Dùng cùng offset 0x5642000000000000L ("VB" in hex) và cùng input hash
            val sourceId = 0x5642000000000000L or
                ((config.metadata.name + lang).hashCode().toLong() and 0xFFFFFFFFL)

            val engine = VBookEngine(client, srcDir)

            val source = JsSource(
                id = sourceId,
                name = config.metadata.name,
                lang = lang,
                engine = engine,
                scripts = scripts,
                isNovel = config.metadata.type == "novel"
            )

            JsExtensionInfo(
                source = source,
                versionName = config.metadata.resolvedVersionName(),
                versionCode = config.metadata.resolvedVersionCode(),
                isNsfw = config.metadata.tag?.lowercase()?.let {
                    it.contains("18+") || it.contains("nsfw")
                } == true,
                author = config.metadata.author,
                isNovel = config.metadata.type == "novel"
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to load JS extension from ${pluginDir.absolutePath}: ${e.message}" }
            null
        }
    }
}

data class JsExtensionInfo(
    val source: Source,
    val versionName: String,
    val versionCode: Long,
    val isNsfw: Boolean,
    val author: String?,
    val isNovel: Boolean = true  // VBook default là novel; comic type sẽ false
)
