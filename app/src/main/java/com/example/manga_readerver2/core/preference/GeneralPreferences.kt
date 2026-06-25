package com.example.manga_readerver2.core.preference

import com.example.manga_readerver2.core.utils.PreferenceStore

class GeneralPreferences(preferenceStore: PreferenceStore) {
    val downloadedOnly = preferenceStore.getBoolean("downloaded_only", false)
    val incognitoMode = preferenceStore.getBoolean("incognito_mode", false)
    val autoClearCache = preferenceStore.getBoolean("auto_clear_cache", false)
    val maxCacheSize = preferenceStore.getInt("max_cache_size", 500) // Default 500 MB

    // Auto Backup Settings
    val autoBackup = preferenceStore.getBoolean("auto_backup", false)
    val autoBackupFrequency = preferenceStore.getInt("auto_backup_frequency", 24) // hours
    val maxAutoBackups = preferenceStore.getInt("max_auto_backups", 5)
}
