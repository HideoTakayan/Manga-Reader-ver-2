package com.example.manga_readerver2.core.security

import com.example.manga_readerver2.core.utils.PreferenceStore

class SecurityPreferences(preferenceStore: PreferenceStore) {
    val appLockEnabled = preferenceStore.getBoolean("app_lock_enabled", false)
    // Timeout in minutes (e.g. 0 = always lock, 1 = 1 minute, 5 = 5 minutes)
    val appLockTimeout = preferenceStore.getInt("app_lock_timeout", 0) 
}
