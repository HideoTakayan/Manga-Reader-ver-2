package com.example.manga_readerver2.core.source

import com.example.manga_readerver2.core.utils.PreferenceStore

class SourcePreferences(
    val preferenceStore: PreferenceStore
) {
    val showNsfwSource = preferenceStore.getBoolean("show_nsfw_source", false)
    val enabledLanguages = preferenceStore.getStringSet("enabled_languages", setOf("all"))
    val pinnedSources = preferenceStore.getStringSet("pinned_sources", emptySet<String>())
    val lastUsedSource = preferenceStore.getLong("last_used_source", -1L)
    val extensionInstaller = preferenceStore.getInt("extension_installer", 0) // 0: Legacy, 1: Private
    val allowUntrustedExtensions = preferenceStore.getBoolean("allow_untrusted_extensions", false)
    val localSourceUri = preferenceStore.getString("local_source_uri", "")
}
