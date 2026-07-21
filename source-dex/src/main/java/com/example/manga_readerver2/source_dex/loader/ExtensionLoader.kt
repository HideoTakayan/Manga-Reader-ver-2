package com.example.manga_readerver2.source_dex.loader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.example.manga_readerver2.core.source.Extension
import com.example.manga_readerver2.core.source.LoadResult
import com.example.manga_readerver2.core.source.SourcePreferences
import com.example.manga_readerver2.core.source.TrustExtension
import com.example.manga_readerver2.core.utils.Hash
import com.example.manga_readerver2.core.utils.copyAndSetReadOnlyTo
import com.example.manga_readerver2.source_dex.util.ChildFirstPathClassLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Class that handles the loading of the extensions.
 */
object ExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource.get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    // Trích xuất metadata 'tachiyomix.extensionLib' từ AndroidManifest để kiểm tra tính tương thích
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val LIB_VERSION_MIN = 1.2  // Mở rộng xuống dưới để tương thích extension cũ
    const val LIB_VERSION_MAX = 2.0  // Mở rộng giới hạn phiên bản tối đa để tăng cường khả năng tương thích ngược

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")

    fun loadExtensions(context: Context, trustedSignatures: Set<String> = emptySet()): List<LoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                if (it.canWrite()) {
                    it.setReadOnly()
                }
                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo!!.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { ExtensionInfo(packageInfo = it, isShared = false) }
            ?: emptySequence()

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            .distinctBy { it.packageInfo.packageName }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        return runBlocking {
            val deferred = extPkgs.map {
                async { loadExtension(context, it, trustedSignatures) }
            }
            deferred.awaitAll()
        }
    }

    private suspend fun loadExtension(context: Context, extensionInfo: ExtensionInfo, trustedSignatures: Set<String>): LoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            return LoadResult.Error(Exception("Missing versionName"))
        }

        val metaData = appInfo.metaData ?: android.os.Bundle.EMPTY
        // Ưu tiên đọc phiên bản thư viện mở rộng từ metadata.
        // Trong trường hợp metadata không tồn tại, hệ thống sẽ sử dụng versionName làm phương án dự phòng.
        // Điều này đảm bảo tính chính xác vì các extension có thể định dạng versionName dưới dạng chuỗi (ví dụ: "14.x.y"), trong khi libVersion luôn là số thực.
        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB)
            .takeUnless { it == 0.0f }
            ?.toString()?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            logcat(LogPriority.WARN) {
                "[ExtensionLoader] SKIP $pkgName: libVersion=$libVersion vượt ngoài phạm vi $LIB_VERSION_MIN..$LIB_VERSION_MAX (versionName=$versionName)"
            }
            return LoadResult.Error(Exception("Lib version mismatch: $libVersion"))
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            if (!preferences.allowUntrustedExtensions.get()) {
                logcat(LogPriority.WARN) { "[ExtensionLoader] SKIP $pkgName: không có chữ ký số" }
                return LoadResult.Error(Exception("Not signed"))
            } else {
                logcat(LogPriority.INFO) { "[ExtensionLoader] Bỏ qua kiểm tra chữ ký cho $pkgName (Developer mode)" }
            }
        }
        
        // Vô hiệu hóa cơ chế xác thực chứng chỉ (Trust). Toàn bộ Extension sẽ được tải tự động.
        // if (!trustExtension.isTrusted(pkgInfo, signatures) && !preferences.allowUntrustedExtensions.get() && !signatures.any { it in trustedSignatures }) { ... }

        val isNsfw = metaData.getInt(METADATA_NSFW) == 1
        // Vô hiệu hóa bộ lọc nội dung nhạy cảm (NSFW), cho phép hiển thị tất cả các nguồn.
        // if (!loadNsfwSource && isNsfw) { ... }

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }

        val sources = (metaData.getString(METADATA_SOURCE_CLASS) ?: metaData.getString(METADATA_SOURCE_FACTORY))
            ?.split(";")
            ?.map { it.trim().let { if (it.startsWith(".")) pkgName + it else it } }
            ?.flatMap {
                try {
                    when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                        is Source -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> emptyList()
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR) { "[ExtensionLoader] Lỗi load class $it trong $pkgName: ${e.message}\n${e.stackTraceToString()}" }
                    emptyList()
                }
            } ?: run {
                logcat(LogPriority.WARN) { "[ExtensionLoader] $pkgName: không có METADATA_SOURCE_CLASS hay METADATA_SOURCE_FACTORY" }
                emptyList()
            }

        logcat(LogPriority.INFO) {
            "[ExtensionLoader] OK $pkgName: ${sources.size} source(s) loaded. [libVersion=$libVersion, lang=${sources.filterIsInstance<CatalogueSource>().map { it.lang }.toSet()}]"
        }

        val langs = sources.filterIsInstance<CatalogueSource>().map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        return LoadResult.Success(
            Extension.Installed(
                name = extName,
                pkgName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                lang = lang,
                isNsfw = isNsfw,
                sources = sources,
                pkgFactory = appInfo.metaData.getString(METADATA_SOURCE_FACTORY),
                icon = appInfo.loadIcon(pkgManager),
                isShared = extensionInfo.isShared
            )
        )
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.let { 
                if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory 
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }

        return signatures?.map { Hash.sha256(it.toByteArray()) }
    }

    fun getExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return try {
            val pkgManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
            } else {
                pkgManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String, trustedSignatures: Set<String> = emptySet()): LoadResult? {
        val pkgInfo = getExtensionPackageInfoFromPkgName(context, pkgName) ?: return null
        if (!isPackageAnExtension(pkgInfo)) return null
        return loadExtension(context, ExtensionInfo(pkgInfo, true), trustedSignatures)
    }

    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) sourceDir = apkPath
        if (publicSourceDir == null) publicSourceDir = apkPath
    }

    private data class ExtensionInfo(val packageInfo: PackageInfo, val isShared: Boolean)
}
