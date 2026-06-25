package com.example.manga_readerver2.features.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsScreenModel : ScreenModel {

    private val context: android.content.Context = Injekt.get()
    private val fileManager: com.example.manga_readerver2.core.utils.FileManager = Injekt.get()
    private val backupManager: com.example.manga_readerver2.core.backup.BackupManager = Injekt.get()

    private val generalPreferences: com.example.manga_readerver2.core.preference.GeneralPreferences = Injekt.get()
    private val downloadManager: com.example.manga_readerver2.core.download.DownloadManager = Injekt.get()

    private val _cacheSize = MutableStateFlow("0 B")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val downloadedOnly = generalPreferences.downloadedOnly.asFlow()
    val incognitoMode = generalPreferences.incognitoMode.asFlow()
    
    val autoBackup = generalPreferences.autoBackup.asFlow()
    val autoBackupFrequency = generalPreferences.autoBackupFrequency.asFlow()
    val maxAutoBackups = generalPreferences.maxAutoBackups.asFlow()

    val downloadQueueCount = downloadManager.queueState
        .map { it.size }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        updateCacheSize()
    }

    fun setDownloadedOnly(enabled: Boolean) {
        generalPreferences.downloadedOnly.set(enabled)
    }

    fun setIncognitoMode(enabled: Boolean) {
        generalPreferences.incognitoMode.set(enabled)
    }

    fun setAutoBackup(enabled: Boolean) {
        generalPreferences.autoBackup.set(enabled)
        com.example.manga_readerver2.core.backup.AutoBackupJob.setupTask(
            context, enabled, generalPreferences.autoBackupFrequency.get()
        )
    }

    fun setAutoBackupFrequency(hours: Int) {
        generalPreferences.autoBackupFrequency.set(hours)
        if (generalPreferences.autoBackup.get()) {
            com.example.manga_readerver2.core.backup.AutoBackupJob.setupTask(
                context, true, hours
            )
        }
    }

    fun setMaxAutoBackups(max: Int) {
        generalPreferences.maxAutoBackups.set(max)
    }

    private fun updateCacheSize() {
        screenModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val size = getDirSize(context.cacheDir)
            _cacheSize.value = fileManager.formatBytes(size)
        }
    }

    private fun getDirSize(dir: java.io.File): Long {
        var size: Long = 0
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        }
        return size
    }

    fun clearCache() {
        screenModelScope.launch(Dispatchers.IO) {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            updateCacheSize()
        }
    }

    fun createBackup(uri: android.net.Uri) {
        screenModelScope.launch {
            val success = backupManager.createBackup(uri)
            _message.value = if (success) "Đã tạo bản sao lưu thành công" else "Lỗi khi tạo bản sao lưu"
        }
    }

    // Backup preview state — hiện dialog trước khi restore
    private val _backupPreview = MutableStateFlow<com.example.manga_readerver2.core.backup.model.BackupPreview?>(null)
    val backupPreview: StateFlow<com.example.manga_readerver2.core.backup.model.BackupPreview?> = _backupPreview.asStateFlow()

    private var pendingRestoreUri: android.net.Uri? = null

    /** Bước 1: validate file và hiện preview dialog */
    fun previewRestoreBackup(uri: android.net.Uri) {
        pendingRestoreUri = uri
        screenModelScope.launch {
            val preview = backupManager.previewBackup(uri)
            _backupPreview.value = preview
        }
    }

    /** Bước 2: user confirm → thực hiện restore */
    fun confirmRestoreBackup() {
        val uri = pendingRestoreUri ?: return
        _backupPreview.value = null
        screenModelScope.launch {
            val success = backupManager.restoreBackup(uri)
            _message.value = if (success) "Đã khôi phục dữ liệu thành công" else "Lỗi khi khôi phục dữ liệu"
            pendingRestoreUri = null
        }
    }

    fun dismissBackupPreview() {
        _backupPreview.value = null
        pendingRestoreUri = null
    }

    fun restoreBackup(uri: android.net.Uri) {
        screenModelScope.launch {
            val success = backupManager.restoreBackup(uri)
            _message.value = if (success) "Đã khôi phục dữ liệu thành công" else "Lỗi khi khôi phục dữ liệu"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
