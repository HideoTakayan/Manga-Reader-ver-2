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
import uy.kohesive.injekt.api.get

class BrowseScreenModel(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val extensionApi: ExtensionApi = Injekt.get(),
    private val extensionInstaller: ExtensionInstaller = Injekt.get(),
    private val repoRepository: ExtensionRepoRepository = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get()
) : ScreenModel {

    // Flow cho Extension đã cài đặt (từ Manager)
    val installedExtensions: StateFlow<List<Extension.Installed>> = extensionManager.installedExtensions
    val untrustedExtensions: StateFlow<List<Extension.Untrusted>> = extensionManager.untrustedExtensions

    // Flow cho Extension có sẵn từ kho lưu trữ
    val availableExtensions: StateFlow<List<Extension.Available>> = extensionManager.availableExtensions

    // Trạng thái cài đặt cho từng extension
    private val _installSteps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installSteps: StateFlow<Map<String, InstallStep>> = _installSteps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // Flow cho danh sách nguồn đã ghim
    val pinnedSources = sourcePreferences.pinnedSources.asFlow()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), sourcePreferences.pinnedSources.get())

    val enabledLanguages = sourcePreferences.enabledLanguages.asFlow()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), sourcePreferences.enabledLanguages.get())

    init {
        observeRepos()
    }

    private fun observeRepos() {
        repoRepository.subscribeAll()
            .distinctUntilChanged()  // Fix BUG-17: Không refresh khi data không đổi
            .drop(1)                 // Fix BUG-08: Bỏ qua emission đầu tiên khi mở màn hình
            .onEach { refreshExtensions() }
            .launchIn(screenModelScope)
    }

    fun refreshExtensions() {
        screenModelScope.launch {
            _isRefreshing.value = true
            logcat(LogPriority.DEBUG) { "Bắt đầu làm mới danh sách extension..." }
            
            // Load local first
            extensionManager.loadLocalExtensions()
            
            // Load from repos
            val allAvailable = mutableListOf<Extension.Available>()
            val repos = repoRepository.getAll()
            logcat(LogPriority.DEBUG) { "Tìm thấy ${repos.size} repository trong database" }
            
            repos.forEach { repo ->
                try {
                    val exts = extensionApi.findExtensions(repo.baseUrl)
                    logcat(LogPriority.DEBUG) { "Repo ${repo.baseUrl} trả về ${exts.size} extension" }
                    allAvailable.addAll(exts)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Lỗi khi lấy extension từ ${repo.baseUrl}: ${e.message}" }
                }
            }
            
            logcat(LogPriority.DEBUG) { "Tổng cộng tìm thấy ${allAvailable.size} extension có sẵn" }
            extensionManager.setAvailableExtensions(allAvailable.sortedBy { it.name })
            _isRefreshing.value = false
        }
    }

    fun installExtension(extension: Extension.Available) {
        screenModelScope.launch {
            extensionInstaller.downloadAndInstall(extension).collect { step ->
                _installSteps.value = _installSteps.value.toMutableMap().apply {
                    put(extension.pkgName, step)
                }
                
                // Nếu cài đặt thành công (với JS hoặc Internal APK), load lại list local
                if (step == InstallStep.Installed || step == InstallStep.SystemInstallStarted) {
                    extensionManager.loadLocalExtensions()
                }
            }
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

    fun trustExtension(extension: Extension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    fun importExtension(uri: android.net.Uri) {
        screenModelScope.launch {
            // Fix I3: Thêm loading feedback
            _isRefreshing.value = true
            try {
                extensionManager.importJsExtension(uri)
                // Fix I2: Refresh availableExtensions để update UI (kích hoạt thay đổi state)
                refreshExtensions()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setLanguages(langs: Set<String>) {
        sourcePreferences.enabledLanguages.set(langs)
    }
}
