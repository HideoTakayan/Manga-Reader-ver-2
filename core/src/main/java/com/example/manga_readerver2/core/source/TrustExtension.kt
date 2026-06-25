package com.example.manga_readerver2.core.source

import com.example.manga_readerver2.core.utils.PreferenceStore

class TrustExtension(
    private val preferenceStore: PreferenceStore
) {
    fun isTrusted(pkgInfo: android.content.pm.PackageInfo, signatures: List<String>): Boolean {
        if (signatures.isEmpty()) return false
        val pkgName = pkgInfo.packageName
        val signatureHash = signatures.last()
        val trustedKeys = preferenceStore.getStringSet("trusted_extensions", emptySet<String>()).get()
        return trustedKeys.contains("$pkgName:$signatureHash")
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        val trustedKeys = preferenceStore.getStringSet("trusted_extensions", emptySet<String>())
        val current = trustedKeys.get()
        trustedKeys.set(current + "$pkgName:$signatureHash")
    }
}
