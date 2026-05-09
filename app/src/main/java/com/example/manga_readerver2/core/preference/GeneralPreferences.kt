package com.example.manga_readerver2.core.preference

import com.example.manga_readerver2.core.utils.PreferenceStore

class GeneralPreferences(preferenceStore: PreferenceStore) {
    val downloadedOnly = preferenceStore.getBoolean("downloaded_only", false)
    val incognitoMode = preferenceStore.getBoolean("incognito_mode", false)
}
