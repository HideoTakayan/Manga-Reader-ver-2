package com.example.manga_readerver2.core.source

import android.content.Context
import android.graphics.drawable.Drawable
import com.example.manga_readerver2.source_dex.loader.ExtensionLoader
import com.example.manga_readerver2.source_js.JsSource
import com.example.manga_readerver2.source_js.loader.JsLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Locale

/**
 * The manager of extensions installed as another apk which extend the available sources.
 * Also manages JS extensions loaded from the filesystem.
 */
class ExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustExtension = Injekt.get(),
) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val api = ExtensionApi()
    private val installer by lazy { ExtensionInstaller(context) }

    private val iconMap = mutableMapOf<String, Drawable?>()

    private val installedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Installed>())
    val installedExtensionsFlow = installedExtensionMapFlow.mapExtensions(scope)

    private val availableExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Available>())
    val availableExtensionsFlow = availableExtensionMapFlow.mapExtensions(scope)

    private val untrustedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Untrusted>())
    val untrustedExtensionsFlow = untrustedExtensionMapFlow.mapExtensions(scope)

    // Thư mục chứa JS extensions (tương thích với JsLoader)
    val jsExtensionsBaseDir: File get() = File(context.filesDir, "js_extensions")

    init {
        scope.launch {
            withContext(Dispatchers.IO) {
                initExtensions()
            }
            ExtensionInstallReceiver(InstallationListener()).register(context)
        }
    }

    fun getExtensionPackage(sourceId: Long): String? {
        return installedExtensionsFlow.value.find { extension ->
            extension.sources.any { it.id == sourceId }
        }?.pkgName
    }

    fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = getExtensionPackage(sourceId) ?: return null

        return iconMap[pkgName] ?: iconMap.getOrPut(pkgName) {
            ExtensionLoader.getExtensionPackageInfoFromPkgName(context, pkgName)?.applicationInfo
                ?.loadIcon(context.packageManager)
        }
    }

    private var availableExtensionsSourcesData: Map<Long, StubSource> = emptyMap()

    private fun setupAvailableExtensionsSourcesDataMap(extensions: List<Extension.Available>) {
        if (extensions.isEmpty()) return
        availableExtensionsSourcesData = extensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableExtensionsSourcesData[id]

    /**
     * Load toàn bộ extensions: APK (trusted + untrusted) và JS.
     * Chạy trên IO thread.
     */
    private suspend fun initExtensions() {
        logcat(LogPriority.INFO) { "[ExtensionManager] Bắt đầu scan extensions..." }

        // Lấy danh sách signature fingerprint từ các repo đã thêm
        val repoRepository: com.example.manga_readerver2.domain.repository.ExtensionRepoRepository = Injekt.get()
        val repoSignatures = repoRepository.getAll().map { it.signingKeyFingerprint }.filter { it.isNotBlank() }.toSet()

        // --- APK extensions ---
        val apkResults = ExtensionLoader.loadExtensions(context, repoSignatures)
        logcat(LogPriority.INFO) { "[ExtensionManager] APK scan: ${apkResults.size} kết quả (Auto-trust ${repoSignatures.size} repo keys)" }

        val trusted = mutableMapOf<String, Extension.Installed>()
        val untrusted = mutableMapOf<String, Extension.Untrusted>()

        for (result in apkResults) {
            when (result) {
                is LoadResult.Success -> {
                    trusted[result.extension.pkgName] = result.extension
                    logcat(LogPriority.INFO) {
                        "[ExtensionManager] ✓ APK: ${result.extension.name} (${result.extension.pkgName})" +
                        " — ${result.extension.sources.size} source(s)"
                    }
                }
                is LoadResult.Untrusted -> {
                    untrusted[result.extension.pkgName] = result.extension
                    logcat(LogPriority.WARN) {
                        "[ExtensionManager] ⚠ UNTRUSTED APK: ${result.extension.name} (${result.extension.pkgName})" +
                        " — cần Trust để dùng được. Vào tab 'Phần mở rộng' để trust."
                    }
                }
                is LoadResult.Error -> {
                    // Đã log chi tiết trong ExtensionLoader
                }
            }
        }

        // --- JS extensions ---
        val jsExtensions = loadJsExtensions()
        logcat(LogPriority.INFO) { "[ExtensionManager] JS scan: ${jsExtensions.size} extension(s)" }
        for (jsExt in jsExtensions) {
            trusted[jsExt.pkgName] = jsExt
            logcat(LogPriority.INFO) {
                "[ExtensionManager] ✓ JS: ${jsExt.name} (${jsExt.pkgName})" +
                " — ${jsExt.sources.size} source(s)"
            }
        }

        val oldExtensions = installedExtensionMapFlow.value.values.toList()
        installedExtensionMapFlow.value = trusted
        untrustedExtensionMapFlow.value = untrusted

        // Giải phóng QuickJS instance của các JS Source cũ để chống memory leak native
        for (ext in oldExtensions) {
            for (source in ext.sources) {
                if (source is JsSource) {
                    source.closeEngine()
                }
            }
        }

        logcat(LogPriority.INFO) {
            "[ExtensionManager] Xong: ${trusted.size} trusted, ${untrusted.size} untrusted"
        }

        _isInitialized.value = true
    }

    /**
     * Scan thư mục JS extensions và trả về danh sách Extension.Installed (fake APK wrapper cho JS).
     * Mỗi subfolder trong jsExtensionsBaseDir là một plugin (có plugin.json bên trong).
     */
    private fun loadJsExtensions(): List<Extension.Installed> {
        if (!jsExtensionsBaseDir.exists()) {
            logcat(LogPriority.INFO) {
                "[ExtensionManager] Không có thư mục JS extensions: ${jsExtensionsBaseDir.absolutePath}"
            }
            return emptyList()
        }

        val pluginDirs = jsExtensionsBaseDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        logcat(LogPriority.INFO) {
            "[ExtensionManager] Tìm thấy ${pluginDirs.size} thư mục JS plugin trong ${jsExtensionsBaseDir.absolutePath}"
        }

        val client: OkHttpClient = Injekt.get()
        val result = mutableListOf<Extension.Installed>()

        for (dir in pluginDirs) {
            val info = JsLoader.loadExtension(context, dir, client)
            if (info == null) {
                logcat(LogPriority.WARN) {
                    "[ExtensionManager] JS plugin bị lỗi, bỏ qua: ${dir.name}"
                }
                continue
            }

            // Sử dụng trực tiếp tên thư mục làm pkgName để khớp với danh sách trên Repo
            val pkgName = dir.name

            val installed = Extension.Installed(
                name = info.source.name,
                pkgName = pkgName,
                versionName = info.versionName,
                versionCode = info.versionCode,
                libVersion = 1.5, // JS extensions dùng version ổn định
                lang = (info.source as? JsSource)?.lang ?: "all",
                isNsfw = info.isNsfw,
                author = info.author,
                pkgFactory = null,
                sources = listOf(info.source),
                icon = null,
                isShared = false,
            )
            result.add(installed)
        }

        return result
    }

    /**
     * Force rescan toàn bộ extensions. Gọi sau khi install/trust/uninstall.
     */
    fun refreshInstalledExtensions() {
        scope.launch {
            withContext(Dispatchers.IO) {
                initExtensions()
            }
        }
    }

    suspend fun findAvailableExtensions() {
        val extensions: List<Extension.Available> = try {
            api.findExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to find available extensions: ${e.message}" }
            return
        }

        availableExtensionMapFlow.value = extensions.associateBy { it.pkgName }
        updatedInstalledExtensionsStatuses(extensions)
        setupAvailableExtensionsSourcesDataMap(extensions)
    }

    private fun updatedInstalledExtensionsStatuses(availableExtensions: List<Extension.Available>) {
        if (availableExtensions.isEmpty()) return

        val installedExtensionsMap = installedExtensionMapFlow.value.toMutableMap()
        var changed = false
        for ((pkgName, extension) in installedExtensionsMap) {
            val availableExt = availableExtensions.find { it.pkgName == pkgName }

            if (availableExt == null && !extension.isObsolete) {
                installedExtensionsMap[pkgName] = extension.copy(isObsolete = true)
                changed = true
            } else if (availableExt != null) {
                val hasUpdate = (availableExt.versionCode > extension.versionCode || availableExt.libVersion > extension.libVersion)
                if (extension.hasUpdate != hasUpdate) {
                    installedExtensionsMap[pkgName] = extension.copy(
                        hasUpdate = hasUpdate,
                        repoUrl = availableExt.repoUrl,
                    )
                    changed = true
                }
            }
        }
        if (changed) {
            installedExtensionMapFlow.value = installedExtensionsMap
        }
    }

    fun installExtension(extension: Extension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(extension).onEach { step ->
            if (step == InstallStep.Installed && extension.apkName.endsWith(".zip", ignoreCase = true)) {
                refreshInstalledExtensions()
            }
        }
    }

    fun updateExtension(extension: Extension.Installed): Flow<InstallStep> {
        val availableExt = availableExtensionMapFlow.value[extension.pkgName] ?: return emptyFlow()
        return installExtension(availableExt)
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        // Not implemented in my simple installer yet, but following Mihon's signature
    }

    fun uninstallExtension(pkgName: String) {
        val extension = installedExtensionMapFlow.value[pkgName]
        if (extension != null && extension.sources.any { it is JsSource }) {
            scope.launch(Dispatchers.IO) {
                extension.sources.forEach { if (it is JsSource) it.closeEngine() }
                
                val pluginDirs = jsExtensionsBaseDir.listFiles { f -> f.isDirectory } ?: emptyArray()
                for (dir in pluginDirs) {
                    if (dir.name == pkgName) {
                        dir.deleteRecursively()
                        break
                    }
                }
                
                withContext(Dispatchers.Main) {
                    installedExtensionMapFlow.value -= pkgName
                }
            }
            return
        }
        installer.uninstallApk(pkgName)
    }

    suspend fun trust(extension: Extension.Untrusted) {
        trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)
        untrustedExtensionMapFlow.value -= extension.pkgName
        // Reload extension từ APK sau khi trust để nó vào installedExtensionMapFlow
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ExtensionLoader.loadExtensionFromPkgName(context, extension.pkgName)
            }
            if (result is LoadResult.Success) {
                installedExtensionMapFlow.value += (result.extension.pkgName to result.extension)
                logcat(LogPriority.INFO) {
                    "[ExtensionManager] ✓ Trust & Load: ${result.extension.name} — ${result.extension.sources.size} source(s)"
                }
            } else {
                logcat(LogPriority.ERROR) {
                    "[ExtensionManager] Trust thành công nhưng load lại thất bại: ${extension.pkgName}, result=$result"
                }
            }
        }
    }

    private inner class InstallationListener : ExtensionInstallReceiver.Listener {
        override fun onExtensionInstalled(extension: Extension.Installed) {
            installedExtensionMapFlow.value += (extension.pkgName to extension)
            logcat(LogPriority.INFO) { "[ExtensionManager] Cài mới: ${extension.name}" }
        }

        override fun onExtensionUpdated(extension: Extension.Installed) {
            installedExtensionMapFlow.value += (extension.pkgName to extension)
            logcat(LogPriority.INFO) { "[ExtensionManager] Cập nhật: ${extension.name}" }
        }

        override fun onExtensionUntrusted(extension: Extension.Untrusted) {
            scope.launch {
                val repoRepository: com.example.manga_readerver2.domain.repository.ExtensionRepoRepository = Injekt.get()
                val repoSignatures = repoRepository.getAll().map { it.signingKeyFingerprint }.filter { it.isNotBlank() }.toSet()
                
                if (extension.signatureHash in repoSignatures) {
                    logcat(LogPriority.INFO) { "[ExtensionManager] Tự động tin cậy extension từ repo: ${extension.name}" }
                    trust(extension)
                } else {
                    installedExtensionMapFlow.value -= extension.pkgName
                    untrustedExtensionMapFlow.value += (extension.pkgName to extension)
                    logcat(LogPriority.WARN) {
                        "[ExtensionManager] Extension mới cài chưa được trust: ${extension.name} — vào tab Phần mở rộng để Trust"
                    }
                }
            }
        }

        override fun onPackageUninstalled(pkgName: String) {
            installedExtensionMapFlow.value -= pkgName
            untrustedExtensionMapFlow.value -= pkgName
        }
    }

    private fun <T : Extension> StateFlow<Map<String, T>>.mapExtensions(scope: CoroutineScope): StateFlow<List<T>> {
        return map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())
    }
}
