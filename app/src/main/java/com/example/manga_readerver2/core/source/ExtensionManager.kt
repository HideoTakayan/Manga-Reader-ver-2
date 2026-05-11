package com.example.manga_readerver2.core.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.source.Source
import com.example.manga_readerver2.source_dex.loader.ExtensionLoader
import com.example.manga_readerver2.source_js.loader.JsLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import java.io.File

class ExtensionManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val sourcePreferences: SourcePreferences
) {
    private val _installedExtensions = MutableStateFlow<List<Extension.Installed>>(emptyList())
    val installedExtensions: StateFlow<List<Extension.Installed>> = _installedExtensions

    private val _availableExtensions = MutableStateFlow<List<Extension.Available>>(emptyList())
    val availableExtensions: StateFlow<List<Extension.Available>> = _availableExtensions

    private val _untrustedExtensions = MutableStateFlow<List<Extension.Untrusted>>(emptyList())
    val untrustedExtensions: StateFlow<List<Extension.Untrusted>> = _untrustedExtensions

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources

    private val _stubSources = MutableStateFlow<Map<Long, Extension.Available.AvailableSource>>(emptyMap())
    val stubSources: StateFlow<Map<Long, Extension.Available.AvailableSource>> = _stubSources

    // Receiver scope dùng để launch coroutine bên trong BroadcastReceiver (Mihon pattern)
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val pkgName = intent?.data?.encodedSchemeSpecificPart ?: return
            // Fix: Kh\u00f4ng filter theo t\u00ean package \u2014 \u0111\u1ec3 ExtensionLoader.isPackageAnExtension() t\u1ef1 quy\u1ebft \u0111\u1ecbnh
            // Filter c\u0169 b\u1ecf s\u00f3t extension t\u1eeb c\u00e1c nh\u00e0 ph\u00e1t tri\u1ec3n kh\u00f4ng d\u00f9ng t\u00ean "tachiyomi"/"keiyoushi"
            logcat { "Package changed: $pkgName \u2014 reloading extensions" }
            receiverScope.launch { loadLocalExtensions() }
        }
    }

    init {
        // Dùng ContextCompat.registerReceiver với RECEIVER_NOT_EXPORTED (Mihon pattern - Android 13+ safe)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED // Extension từ app khác (Mihon) cần EXPORTED
        )

        // Load ngay khi khởi tạo
        receiverScope.launch { loadLocalExtensions() }
    }

    suspend fun loadLocalExtensions() {
        withContext(Dispatchers.IO) {
            val loadedExtensions = mutableListOf<Extension.Installed>()
            val untrustedExtensions = mutableListOf<Extension.Untrusted>()
            val loadedSources = mutableListOf<Source>()

            // 1. Load APK Extensions (Mihon Style)
            val trustedSignatures = sourcePreferences.trustedExtensions.get()
            try {
                val loadResults = ExtensionLoader.loadExtensions(context, trustedSignatures)
                loadResults.forEach { result ->
                    when (result) {
                        is LoadResult.Success -> {
                            loadedExtensions.add(result.extension)
                            loadedSources.addAll(result.extension.sources)
                        }
                        is LoadResult.Untrusted -> {
                            untrustedExtensions.add(result.extension)
                        }
                        else -> {}
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR) { "Error loading APK extensions: ${e.message}" }
            }

            // 2. Load JS Extensions (VBook Style)
            val jsDir = File(context.filesDir, "extensions/js")
            if (jsDir.exists()) {
                jsDir.listFiles { file -> file.isDirectory }?.forEach { pluginDir ->
                    try {
                        val info = JsLoader.loadExtension(context, pluginDir, client)
                        if (info != null) {
                            val source = info.source
                            val iconFile = File(pluginDir, "icon.png")
                            val iconDrawable = if (iconFile.exists()) {
                                android.graphics.drawable.BitmapDrawable(
                                    context.resources,
                                    android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
                                )
                            } else null

                            val vbookExt = Extension.Installed(
                                name = source.name,
                                pkgName = "vbook.${pluginDir.name}",
                                versionName = info.versionName,
                                versionCode = info.versionCode,
                                libVersion = 1.0,
                                lang = source.lang,
                                isNsfw = info.isNsfw,
                                pkgFactory = null,
                                sources = listOf(source),
                                icon = iconDrawable,
                                isShared = false,
                                isVBook = true,
                                author = info.author
                            )
                            loadedExtensions.add(vbookExt)
                            loadedSources.add(source)
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR) { "Error loading JS extension ${pluginDir.name}: ${e.message}" }
                    }
                }
            }

            // Snapshot list cũ trước khi update StateFlow.
            // Phải update StateFlow TRƯỚC khi close engine — nếu close throw thì StateFlow vẫn được cập nhật.
            val oldExtensions = _installedExtensions.value

            _installedExtensions.value = loadedExtensions
            _untrustedExtensions.value = untrustedExtensions
            _sources.value = loadedSources
            logcat { "Loaded ${loadedSources.size} sources from ${loadedExtensions.size} extensions." }

            // Đóng engine cũ sau khi StateFlow đã được cập nhật để tránh leak native QuickJS heap
            oldExtensions.forEach { ext ->
                ext.sources.filterIsInstance<com.example.manga_readerver2.source_js.JsSource>()
                    .forEach { jsSource ->
                        try { jsSource.closeEngine() } catch (e: Throwable) {
                            logcat(LogPriority.WARN) { "Failed to close JS engine: ${e.message}" }
                        }
                    }
            }
        }
    }

    fun setAvailableExtensions(extensions: List<Extension.Available>) {
        _availableExtensions.value = extensions
        _stubSources.value = extensions.flatMap { it.sources }.associateBy { it.id }
    }

    fun getSource(sourceId: Long): Source? {
        return _sources.value.find { it.id == sourceId }
    }

    fun getStubSource(sourceId: Long): Extension.Available.AvailableSource? {
        return _stubSources.value[sourceId]
    }

    suspend fun importJsExtension(uri: android.net.Uri) {
        withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val fileName = getFileName(context, uri) ?: "imported_${System.currentTimeMillis()}.js"
                val ext = fileName.substringAfterLast(".", "").lowercase()

                if (ext == "zip") {
                    // Import file ZIP — giải nén vào extensions/js/
                    val pluginId = fileName.substringBeforeLast(".").replace(" ", "_").lowercase()
                    val tmpFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")

                    contentResolver.openInputStream(uri)?.use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    // Tái sử dụng logic giải nén từ ExtensionInstaller
                    val pluginDir = File(context.filesDir, "extensions/js/$pluginId")
                    if (pluginDir.exists()) pluginDir.deleteRecursively()
                    pluginDir.mkdirs()

                    java.util.zip.ZipFile(tmpFile).use { zip ->
                        val entries = zip.entries().asSequence().toList()
                        // Fix: lọc directory entries trước để tránh false "single root" detection
                        val firstLevels = entries
                            .filter { !it.isDirectory }
                            .map { it.name.substringBefore("/") }
                            .distinct()
                        val hasSingleRoot = firstLevels.size == 1 && entries.any { it.name.contains("/") }
                        val rootFolder = if (hasSingleRoot) firstLevels.first() else null

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
                                zip.getInputStream(entry).use { i -> entryFile.outputStream().use { o -> i.copyTo(o) } }
                            }
                        }
                    }
                    tmpFile.delete()
                    logcat(LogPriority.DEBUG) { "Import ZIP extension thành công: $pluginId" }

                } else {
                    // Import file JS — tạo plugin đơn gản
                    val pluginId = fileName.substringBeforeLast(".").replace(" ", "_").lowercase()
                    val pluginDir = File(context.filesDir, "extensions/js/$pluginId")
                    val srcDir = File(pluginDir, "src")
                    if (!srcDir.exists()) srcDir.mkdirs()

                    contentResolver.openInputStream(uri)?.use { input ->
                        File(srcDir, fileName).outputStream().use { output -> input.copyTo(output) }
                    }

                    // Tạo plugin.json mặc định nếu chưa có
                    val pluginJsonFile = File(pluginDir, "plugin.json")
                    if (!pluginJsonFile.exists()) {
                        val safeName = fileName.substringBeforeLast(".")
                        // Fix I1: Sử dụng fileName thay vì hardcode "home.js" hoặc tên không sanitize
                        // Trong trường hợp này fileName chính là tên file đang được copy
                        pluginJsonFile.writeText("""
                            {
                                "metadata": {
                                    "name": "$safeName",
                                    "author": "Imported",
                                    "version": "1.0.0",
                                    "language": "all",
                                    "locale": "all"
                                },
                                "script": {
                                    "home": "$fileName",
                                    "search": "$fileName",
                                    "detail": "$fileName",
                                    "toc": "$fileName",
                                    "chap": "$fileName"
                                }
                            }
                        """.trimIndent())
                    }
                    logcat(LogPriority.DEBUG) { "Import JS extension thành công: $pluginId" }
                }

                loadLocalExtensions()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Lỗi khi nhập extension: ${e.message}" }
            }
        }
    }

    private fun getFileName(context: Context, uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    suspend fun trust(extension: Extension.Untrusted) {
        val trustKey = "${extension.pkgName}:${extension.versionCode}:${extension.signatureHash}"
        val currentTrusted = sourcePreferences.trustedExtensions.get()
        sourcePreferences.trustedExtensions.set(currentTrusted + trustKey)
        loadLocalExtensions()
    }

    suspend fun uninstallExtension(pkgName: String) {
        withContext(Dispatchers.IO) {
            try {
                if (pkgName.startsWith("vbook.")) {
                    val pluginId = pkgName.substringAfter("vbook.")
                    val pluginDir = File(context.filesDir, "extensions/js/$pluginId")
                    if (pluginDir.exists()) {
                        pluginDir.deleteRecursively()
                    }
                } else {
                    // 1. Xóa file APK nội bộ nếu có
                    val privateExtDir = File(context.filesDir, "exts")
                    val privateFile = File(privateExtDir, "$pkgName.apk")
                    if (privateFile.exists()) {
                        privateFile.delete()
                        logcat(LogPriority.DEBUG) { "Đã xóa file APK nội bộ: $pkgName" }
                    }

                    // 2. Gọi gỡ cài đặt hệ thống (nếu đã cài vào hệ thống)
                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                        data = android.net.Uri.parse("package:$pkgName")
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Lỗi khi gỡ cài đặt extension $pkgName: ${e.message}" }
            }
            loadLocalExtensions()
        }
    }
}
