package com.example.manga_readerver2.core.preference

import com.example.manga_readerver2.core.utils.PreferenceStore

class DisplayPreferences(preferenceStore: PreferenceStore) {
    val appTheme = preferenceStore.getString("app_theme", "DEFAULT")
    val dynamicColor = preferenceStore.getBoolean("theme_dynamic_color", true)
    val pureBlack = preferenceStore.getBoolean("theme_pure_black", false)
}
