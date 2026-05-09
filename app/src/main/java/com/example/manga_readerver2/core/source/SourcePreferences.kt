package com.example.manga_readerver2.core.source

import com.example.manga_readerver2.core.utils.PreferenceStore

/**
 * Quản lý các tùy chọn liên quan đến nguồn truyện.
 */
class SourcePreferences(preferenceStore: PreferenceStore) {

    val enabledLanguages = preferenceStore.getStringSet("source_languages", setOf("vi", "en"))
    
    val extensionUpdatesCount = preferenceStore.getInt("ext_updates_count", 0)

    val trustedExtensions = preferenceStore.getStringSet("trusted_extensions", emptySet())
    
    val showNsfwSource = preferenceStore.getBoolean("show_nsfw_source", true)

    val pinnedSources = preferenceStore.getStringSet("pinned_sources", emptySet())

    val extensionInstaller = preferenceStore.getInt("extension_installer", 0) // 0: System, 1: Internal
}
