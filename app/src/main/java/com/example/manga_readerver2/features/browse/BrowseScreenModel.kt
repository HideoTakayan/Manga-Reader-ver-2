package com.example.manga_readerver2.features.browse

import logcat.LogPriority
import logcat.logcat

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.core.source.Extension
import com.example.manga_readerver2.core.source.ExtensionApi
import com.example.manga_readerver2.core.source.ExtensionInstaller
import com.example.manga_readerver2.core.source.ExtensionManager
import com.example.manga_readerver2.core.source.InstallStep
import com.example.manga_readerver2.domain.repository.ExtensionRepoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import com.example.manga_readerver2.core.source.SourcePreferences
import uy.kohesive.injekt.Injekt
import com.example.manga_readerver2.core.source.SourceManager
import uy.kohesive.injekt.api.get
import android.content.Context
import android.net.Uri
import android.content.Intent

class BrowseScreenModel(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get<SourceManager>(),
    private val extensionApi: ExtensionApi = Injekt.get(),
    private val extensionInstaller: ExtensionInstaller = Injekt.get(),
    private val repoRepository: ExtensionRepoRepository = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val context: Context = Injekt.get()
) : ScreenModel {

    // Flow cho Extension đã cài đặt (từ Manager)
    val installedExtensions: StateFlow<List<Extension.Installed>> = extensionManager.installedExtensionsFlow
    val untrustedExtensions: StateFlow<List<Extension.Untrusted>> = extensionManager.untrustedExtensionsFlow

    // Nguồn truyện (từ SourceManager)
    val catalogueSources: StateFlow<List<eu.kanade.tachiyomi.source.CatalogueSource>> = sourceManager.catalogueSources.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Flow cho Extension có sẵn từ kho lưu trữ
    private val _availableExtensions = MutableStateFlow<List<Extension.Available>>(emptyList())
    val availableExtensions: StateFlow<List<Extension.Available>> = _availableExtensions.asStateFlow()

    // Trạng thái cài đặt cho từng extension
    private val _installSteps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installSteps: StateFlow<Map<String, InstallStep>> = _installSteps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val pinnedSources = sourcePreferences.pinnedSources.asFlow()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), sourcePreferences.pinnedSources.get())

    val hiddenSources = sourcePreferences.hiddenSources.asFlow()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), sourcePreferences.hiddenSources.get())

    val enabledLanguages = sourcePreferences.enabledLanguages.asFlow()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), sourcePreferences.enabledLanguages.get())

    init {
        refreshExtensions() // BUG-6 fix: Khởi tạo fetch data chủ động một lần
        observeRepos()
        observeInstalledExtensions()
    }

    private fun observeInstalledExtensions() {
        installedExtensions.onEach { installed ->
            val installedPkgs = installed.map { it.pkgName }
            val currentSteps = _installSteps.value
            var changed = false
            val newSteps = currentSteps.mapValues { (pkgName, step) ->
                if (pkgName in installedPkgs && step == InstallStep.SystemInstallStarted) {
                    changed = true
                    InstallStep.Installed
                } else {
                    step
                }
            }
            if (changed) {
                _installSteps.value = newSteps
            }
        }.launchIn(screenModelScope)
    }

    private fun observeRepos() {
        repoRepository.subscribeAll()
            .drop(1) // BUG-6 fix: Bỏ qua lần phát đầu tiên vì init đã fetch rồi
            .distinctUntilChanged()  // Fix BUG-17: Không refresh khi data không đổi
            .onEach { refreshExtensions() }
            .launchIn(screenModelScope)
    }

    fun refreshExtensions() {
        screenModelScope.launch {
            _isRefreshing.value = true
            try {
                var exts = extensionApi.findExtensions()
                // Bỏ qua bộ lọc NSFW, luôn luôn hiện tất cả extension (cả 18+)
                _availableExtensions.value = exts
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Lỗi khi lấy extension: ${e.message}" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun installExtension(extension: Extension.Available) {
        screenModelScope.launch {
            extensionInstaller.downloadAndInstall(extension).collect { step ->
                _installSteps.value = _installSteps.value.toMutableMap().apply {
                    put(extension.pkgName, step)
                }
                
                // Chỉ reload ngay khi đã cài xong thật sự (JS/Internal APK).
                // Với SystemInstallStarted, chờ package broadcast hoặc onScreenResumed().
                if (step == InstallStep.Installed) {
                    extensionManager.refreshInstalledExtensions()
                }
            }
        }
    }

    fun onScreenResumed() {
        // Sau khi người dùng quay lại từ system install dialog, refresh để hiện extension mới
        screenModelScope.launch {
            extensionManager.refreshInstalledExtensions()
        }
    }

    fun uninstallExtension(pkgName: String) {
        screenModelScope.launch {
            extensionManager.uninstallExtension(pkgName)
            refreshExtensions()
        }
    }


    fun togglePin(sourceId: Long) {
        val currentPinned = sourcePreferences.pinnedSources.get()
        val newPinned = if (currentPinned.contains(sourceId.toString())) {
            currentPinned - sourceId.toString()
        } else {
            currentPinned + sourceId.toString()
        }
        sourcePreferences.pinnedSources.set(newPinned)
    }

    fun toggleHide(sourceId: Long) {
        val currentHidden = sourcePreferences.hiddenSources.get()
        val newHidden = if (currentHidden.contains(sourceId.toString())) {
            currentHidden - sourceId.toString()
        } else {
            currentHidden + sourceId.toString()
        }
        sourcePreferences.hiddenSources.set(newHidden)
    }

    fun trustExtension(extension: Extension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
            // trust() đã tự reload extension vào installedExtensionMapFlow
            // AndroidSourceManager sẽ tự động cập nhật qua collectLatest
        }
    }


    fun setLanguages(langs: Set<String>) {
        sourcePreferences.enabledLanguages.set(langs)
    }

    fun getJsExtensionsPath(): String {
        return extensionManager.jsExtensionsBaseDir.absolutePath
    }

    fun refreshInstalledExtensions() {
        screenModelScope.launch {
            extensionManager.refreshInstalledExtensions()
        }
    }

    fun updateLocalSourceUri(uriStr: String) {
        val uri = Uri.parse(uriStr)
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sourcePreferences.localSourceUri.set(uriStr)
    }
}
