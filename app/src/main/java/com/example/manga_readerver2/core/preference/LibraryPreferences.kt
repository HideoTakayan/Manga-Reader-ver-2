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
}
