package com.example.manga_readerver2.core.preference

import com.example.manga_readerver2.core.utils.PreferenceStore
import com.example.manga_readerver2.features.library.LibraryDisplayMode

class LibraryPreferences(preferenceStore: PreferenceStore) {
    val displayMode = preferenceStore.getInt("library_display_mode", LibraryDisplayMode.CompactGrid.ordinal)
    val showDownloadBadges = preferenceStore.getBoolean("library_show_download_badges", true)
    val showUnreadBadges = preferenceStore.getBoolean("library_show_unread_badges", true)
    val showCategoryTabs = preferenceStore.getBoolean("library_show_category_tabs", true)
    
    val filterDownloaded = preferenceStore.getInt("library_filter_downloaded", TriState.DISABLED.ordinal)
    val filterUnread = preferenceStore.getInt("library_filter_unread", TriState.DISABLED.ordinal)
    val filterStarted = preferenceStore.getInt("library_filter_started", TriState.DISABLED.ordinal)
    val filterBookmarked = preferenceStore.getInt("library_filter_bookmarked", TriState.DISABLED.ordinal)
    
    // Background Update settings
    // 0 = Manual, 12 = 12 hours, 24 = 24 hours, 48 = 48 hours
    val updateInterval = preferenceStore.getInt("library_update_interval", 0)
    val updateWifiOnly = preferenceStore.getBoolean("library_update_wifi_only", true)

    // Sort mode: 0=DATE_ADDED, 1=TITLE, 2=LAST_UPDATE
    val sortMode = preferenceStore.getInt("library_sort_mode", 0)
}
