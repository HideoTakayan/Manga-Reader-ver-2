package com.example.manga_readerver2.source_dex.loader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.example.manga_readerver2.core.source.Extension
import com.example.manga_readerver2.core.source.LoadResult
import com.example.manga_readerver2.core.utils.Hash
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import com.example.manga_readerver2.source_dex.util.ChildFirstPathClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat
import java.io.File

/**
 * Lớp chịu trách nhiệm nạp các extension chuẩn Mihon (APK/DEX).
 * Hỗ trợ nạp từ hệ thống (Shared) hoặc thư mục riêng (Private).
 */
@SuppressLint("PackageManagerGetSignatures")
object ExtensionLoader {

    const val LIB_VERSION_MIN = 1.4
    const val LIB_VERSION_MAX = 1.6

    // Metadata keys chuẩn Mihon
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    private const val METADATA_LIB_VERSION = "tachiyomi.extension.lib.version"

    private const val EXTENSION_FEATURE = "tachiyomi.extension"

    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    fun loadExtensions(context: Context, trustedSignatures: Set<String>): List<LoadResult> {
        val pkgManager = context.packageManager
        
        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }
        
        val sharedExtPkgs = installedPkgs
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(it, true) }

        val privateExtDir = File(context.filesDir, "exts")
        val privatePkgs = privateExtDir.listFiles()
            ?.filter { it.isFile && it.extension == "apk" }
            ?.mapNotNull { file ->
                val path = file.absolutePath
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pkgManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
                } else {
                    pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                }
                pkgInfo?.apply {
                    applicationInfo?.sourceDir = path
                    applicationInfo?.publicSourceDir = path
                }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { ExtensionInfo(it, false) }
            ?: emptyList()

        val allPkgs = (sharedExtPkgs + privatePkgs)
            .groupBy { it.packageInfo.packageName }
            .map { (_, versions) ->
                versions.maxByOrNull { PackageInfoCompat.getLongVersionCode(it.packageInfo) }!!
            }

        return runBlocking {
            allPkgs.map { pkg ->
                async { loadExtension(context, pkg, trustedSignatures) }
            }.awaitAll()
        }
    }

    fun loadExtensionFromPkgName(context: Context, pkgName: String, trustedSignatures: Set<String>): LoadResult {
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
            } else {
                context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
            }
        } catch (e: Exception) {
            null
        } ?: return LoadResult.Error

        return loadExtension(context, ExtensionInfo(pkgInfo, true), trustedSignatures)
    }

    private fun loadExtension(context: Context, info: ExtensionInfo, trustedSignatures: Set<String>): LoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = info.packageInfo
        val appInfo = pkgInfo.applicationInfo ?: return LoadResult.Error
        val metaData = appInfo.metaData
        val pkgName = pkgInfo.packageName

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName ?: ""
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        // Đọc libVersion từ metadata key chuẩn (tachiyomi.extension.lib.version)
        // Mihon APK có key này với giá trị như "1.5", "1.4" ...
        // versionName là version của extension ("14.10.5") — KHÔNG phải libVersion
        val libVersion = metaData?.getString(METADATA_LIB_VERSION)?.toDoubleOrNull()
            ?: metaData?.getFloat(METADATA_LIB_VERSION, 0f)?.takeIf { it > 0f }?.toDouble()
            // Fallback: thử parse versionName theo format "major.minor" (chỉ lấy 2 phần đầu)
            ?: run {
                val parts = versionName.split(".")
                if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDoubleOrNull() else null
            }
            ?: 1.5  // Default safe: assume 1.5 nếu không đọc được

        // System-installed extensions: bypass libVersion check (user đã verify qua Android installer)
        // Private APK: vẫn cần check để tránh load extension incompatible
        if (!info.isShared && (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX)) {
            logcat(LogPriority.WARN) { "Extension $extName (private) có libVersion $libVersion không tương thích" }
            return LoadResult.Error
        }
        if (info.isShared && libVersion != 0.0 && libVersion < 1.0) {
            // Chỉ reject nếu rõ ràng là version invalid (< 1.0)
            logcat(LogPriority.WARN) { "Extension $extName có libVersion $libVersion không hợp lệ" }
            return LoadResult.Error
        }

        val signatures = getSignatures(pkgInfo)
        val signatureHash = signatures.lastOrNull() ?: ""
        val trustKey = "$pkgName:$versionCode:$signatureHash"

        // Extension cài từ hệ thống (isShared=true) = user đã xác nhận qua Android system installer
        // → auto-trust, không cần xác nhận thêm.
        // Chỉ private APK (internal, isShared=false) mới cần trust thủ công.
        val requiresTrust = !info.isShared
        if (requiresTrust && !trustedSignatures.contains(trustKey)) {
            val extension = Extension.Untrusted(
                name = extName,
                pkgName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                signatureHash = signatureHash,
                lang = null,
                isNsfw = false
            )
            return LoadResult.Untrusted(extension)
        }

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo?.sourceDir ?: "", null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Không thể tạo ClassLoader cho $pkgName: ${e.message}" }
            return LoadResult.Error
        }

        // Hỗ trợ cả 2 key metadata vì một số extension chỉ khai báo factory.
        val sourceClassRaw = metaData?.getString(METADATA_SOURCE_CLASS)
            ?: metaData?.getString(METADATA_SOURCE_FACTORY)
        if (sourceClassRaw.isNullOrBlank()) {
            logcat(LogPriority.ERROR) {
                "Extension $pkgName thiếu metadata '${METADATA_SOURCE_CLASS}' hoặc '${METADATA_SOURCE_FACTORY}'"
            }
            return LoadResult.Error
        }

        val sources = sourceClassRaw
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { className ->
                // Class name bắt đầu bằng '.' là relative → thêm package name phía trước
                if (className.startsWith(".")) pkgName + className else className
            }
            .flatMap { className ->
                try {
                    when (val obj = Class.forName(className, false, classLoader)
                        .getDeclaredConstructor().newInstance()) {
                        is SourceFactory -> obj.createSources()   // Multi-source extension
                        is Source        -> listOf(obj)            // Single-source extension
                        else -> {
                            logcat(LogPriority.ERROR) { "Unknown source type: ${obj.javaClass} in $pkgName" }
                            return LoadResult.Error
                        }
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR) { "Lỗi khi nạp class '$className' từ $pkgName: ${e.message}" }
                    return LoadResult.Error
                }
            }

        if (sources.isEmpty()) {
            logcat(LogPriority.ERROR) { "Extension $pkgName không có source nào" }
            return LoadResult.Error
        }

        val isNsfw = metaData?.getInt(METADATA_NSFW) == 1
        val lang = sources.filterIsInstance<CatalogueSource>().map { it.lang }.distinct().let {
            if (it.size == 1) it.first() else "all"
        }

        val extension = Extension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            pkgFactory = appInfo?.metaData?.getString(METADATA_SOURCE_FACTORY),
            sources = sources,
            icon = appInfo?.loadIcon(pkgManager),
            isShared = info.isShared,
            isVBook = false
        )

        return LoadResult.Success(extension)
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        // Fix BUG-01: Một số extension cũ hoặc custom không khai báo reqFeatures đúng chuẩn Mihon
        // Cần fallback kiểm tra metadata 'tachiyomi.extension.class' và package prefix
        return pkgInfo.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true ||
               pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true ||
               pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_FACTORY) == true ||
               pkgInfo.packageName.startsWith("eu.kanade.tachiyomi.extension.") ||
               pkgInfo.packageName.startsWith("mihon.extension.") ||
               pkgInfo.packageName.startsWith("com.example.manga_readerver2.extension.")
    }

    private fun getSignatures(pkgInfo: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
        } else {
            pkgInfo.signatures
        }

        return signatures?.map { Hash.sha256(it.toByteArray()) } ?: emptyList()
    }

    private data class ExtensionInfo(val packageInfo: PackageInfo, val isShared: Boolean)
}
