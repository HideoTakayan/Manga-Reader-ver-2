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
        // Lưu ý: Tệp plugin.json sử dụng định dạng Int cho thuộc tính "version", trong khi Model yêu cầu String. Quá trình tiền xử lý được áp dụng để đồng bộ kiểu dữ liệu.
        coerceInputValues = true
        isLenient = true
    }

    fun loadExtension(context: Context, pluginDir: File, client: OkHttpClient): JsExtensionInfo? {
        var pluginJsonFile = File(pluginDir, "plugin.json")
        var effectivePluginDir = pluginDir

        if (!pluginJsonFile.exists()) {
            val subDirs = pluginDir.listFiles { f -> f.isDirectory }
            if (subDirs != null && subDirs.size == 1) {
                val subDir = subDirs[0]
                val nestedPluginJson = File(subDir, "plugin.json")
                if (nestedPluginJson.exists()) {
                    effectivePluginDir = subDir
                    pluginJsonFile = nestedPluginJson
                }
            }
        }

        if (!pluginJsonFile.exists()) {
            logcat(LogPriority.WARN) { "plugin.json not found in ${pluginDir.absolutePath}" }
            return null
        }

        return try {
            val rawText = pluginJsonFile.readText()
            // kotlinx.serialization crashes on trailing commas. Clean them up manually.
            val configText = rawText.replace(Regex(",(?=\\s*[}\\]])"), "")
            val config = json.decodeFromString<PluginConfig>(configText)
            
            val scripts = mutableMapOf<String, String>()
            val srcDir = File(effectivePluginDir, "src")
            
            // Load all JS files in effectivePluginDir and srcDir to make them available for dynamic execution
            effectivePluginDir.listFiles { file -> file.extension == "js" }?.forEach { file ->
                scripts[file.nameWithoutExtension] = file.readText()
            }
            if (srcDir.exists() && srcDir.isDirectory) {
                srcDir.listFiles { file -> file.extension == "js" }?.forEach { file ->
                    scripts[file.nameWithoutExtension] = file.readText()
                }
            }
            
            // Also load explicitly defined scripts in root just in case
            fun readScript(name: String?, key: String) {
                if (name == null) return
                val rootFile = File(effectivePluginDir, name)
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

            // Xác minh tính toàn vẹn của các tập lệnh cốt lõi (toc và chap)
            val mandatoryScripts = listOf("toc", "chap")
            val missingScripts = mandatoryScripts.filter { !scripts.containsKey(it) }
            if (missingScripts.isNotEmpty()) {
                logcat(LogPriority.WARN) {
                    "[JsLoader] ${pluginDir.name}: Thiếu script bắt buộc: $missingScripts. " +
                    "Plugin có thể tải được nhưng không đọc truyện được."
                }
            }

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

            // ID phải khớp với ExtensionApi.sourceId formula
            // Tính toán mã định danh bằng cách kết hợp hàm băm với giá trị bù trừ chuẩn (offset 0x5642000000000000L)
            val sourceId = 0x5642000000000000L or
                ((config.metadata.name + lang).hashCode().toLong() and 0xFFFFFFFFL)

            val engine = VBookEngine(client, effectivePluginDir)

            val source = JsSource(
                id = sourceId,
                name = config.metadata.name,
                lang = lang,
                engine = engine,
                scripts = scripts,
                isNovel = config.metadata.type == "novel"
            )

            val iconFile = File(effectivePluginDir, "icon.png")
            val iconDrawable = if (iconFile.exists()) {
                android.graphics.drawable.BitmapDrawable(context.resources, android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath))
            } else null

            JsExtensionInfo(
                source = source,
                versionName = config.metadata.resolvedVersionName(),
                versionCode = config.metadata.resolvedVersionCode(),
                isNsfw = config.metadata.tag?.lowercase()?.let {
                    it.contains("18+") || it.contains("nsfw")
                } == true,
                author = config.metadata.author,
                isNovel = config.metadata.type == "novel",
                icon = iconDrawable
            )
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Failed to load JS extension from ${pluginDir.absolutePath}: ${e.message}\n${e.stackTraceToString()}" }
            try {
                java.io.File(context.cacheDir, "js_error_${pluginDir.name}.txt").writeText("Error: ${e.message}\n${e.stackTraceToString()}")
            } catch (ex: Exception) {}
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
    val isNovel: Boolean = true,  // Tham số định cấu hình loại nội dung (mặc định là tiểu thuyết)
    val icon: android.graphics.drawable.Drawable? = null
)
