package com.example.manga_readerver2.core.preference

import com.example.manga_readerver2.core.utils.PreferenceStore

class DownloadPreferences(preferenceStore: PreferenceStore) {
    val downloadOnlyOverWifi = preferenceStore.getBoolean("download_only_over_wifi", true)
    val autoDeleteAfterReading = preferenceStore.getBoolean("download_auto_delete", false)
}
