package com.example.manga_readerver2.core.source

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.source.Source

sealed class Extension {
    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean
    abstract val author: String?

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        override val author: String? = null,
        val pkgFactory: String?,
        val sources: List<Source>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,
        val repoUrl: String? = null,
        val isVBook: Boolean = false // Thêm trường để phân biệt VBook
    ) : Extension()

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        override val author: String? = null,
        val sources: List<AvailableSource>,
        val apkName: String,
        val iconUrl: String,
        val repoUrl: String,
        val isVBook: Boolean = false // Thêm trường để phân biệt VBook
    ) : Extension() {
        data class AvailableSource(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
        )
    }

    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        override val author: String? = null,
    ) : Extension()
}
