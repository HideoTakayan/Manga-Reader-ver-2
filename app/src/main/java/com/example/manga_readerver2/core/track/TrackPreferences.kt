package com.example.manga_readerver2.core.track

import com.example.manga_readerver2.core.utils.PreferenceStore

class TrackPreferences(preferenceStore: PreferenceStore) {
    val anilistToken = preferenceStore.getString("anilist_token", "")
    val anilistUsername = preferenceStore.getString("anilist_username", "")
}
