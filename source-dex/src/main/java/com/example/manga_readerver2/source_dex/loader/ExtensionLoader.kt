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
    const val LIB_VERSION_MAX = 2.0

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

    // Mihon compatibility: Parse lib version from version name if not in metadata
    private fun parseLibVersion(versionName: String): Double {
        if (versionName.isEmpty()) return 1.5
        val parts = versionName.split(".")
        return if (parts.size >= 2) {
            "${parts[0]}.${parts[1]}".toDoubleOrNull() ?: 1.5
        } else {
            versionName.toDoubleOrNull() ?: 1.5
        }
    }

    fun loadExtensions(context: Context, trustedSignatures: Set<String>): List<LoadResult> {
        val pkgManager = context.packageManager
        
        val installedPkgs = mutableListOf<PackageInfo>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong())
                installedPkgs.addAll(pkgManager.getInstalledPackages(flags))
            } else {
                @Suppress("DEPRECATION")
                installedPkgs.addAll(pkgManager.getInstalledPackages(PACKAGE_FLAGS))
            }
            
            // Cách 2: Quét qua Intent (chắc chắn hơn cho extension) - Quét cả Activity, Service và Receiver
            val extensionActions = listOf(
                "tachiyomi.extension",
                "eu.kanade.tachiyomi.extension.ACTION_LOAD",
                "tachiyomi.extension.ACTION_LOAD",
                "mihon.extension.ACTION_LOAD"
            )
            
            extensionActions.forEach { action ->
                val intent = Intent(action)
                // Thu thập pkgName từ 3 nguồn: Activities, Services, Receivers
                val pkgNames = mutableSetOf<String>()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val flags = PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    pkgNames.addAll(pkgManager.queryIntentActivities(intent, flags).map { res -> res.activityInfo.packageName })
                    pkgNames.addAll(pkgManager.queryIntentServices(intent, flags).map { res -> res.serviceInfo.packageName })
                    pkgNames.addAll(pkgManager.queryBroadcastReceivers(intent, flags).map { res -> res.activityInfo.packageName })
                } else {
                    val flags = PackageManager.GET_META_DATA
                    @Suppress("DEPRECATION")
                    pkgNames.addAll(pkgManager.queryIntentActivities(intent, flags).map { res -> res.activityInfo.packageName })
                    @Suppress("DEPRECATION")
                    pkgNames.addAll(pkgManager.queryIntentServices(intent, flags).map { res -> res.serviceInfo.packageName })
                    @Suppress("DEPRECATION")
                    pkgNames.addAll(pkgManager.queryBroadcastReceivers(intent, flags).map { res -> res.activityInfo.packageName })
                }
                
                pkgNames.forEach { pkgName ->
                    if (installedPkgs.none { it.packageName == pkgName }) {
                        try {
                            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pkgManager.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
                            } else {
                                @Suppress("DEPRECATION")
                                pkgManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                            }
                            installedPkgs.add(pInfo)
                            logcat(LogPriority.DEBUG) { "Tìm thấy extension qua Intent: $pkgName" }
                        } catch (e: Exception) {
                            // Bỏ qua nếu không lấy được info
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Lỗi khi quét package: ${e.message}" }
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
        val pkgName = info.packageInfo.packageName
        return try {
            loadExtensionInternal(context, info, trustedSignatures)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Lỗi nghiêm trọng khi nạp extension $pkgName: ${e.message}\n${e.stackTraceToString()}" }
            LoadResult.Error
        }
    }

    private fun loadExtensionInternal(context: Context, info: ExtensionInfo, trustedSignatures: Set<String>): LoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = info.packageInfo
        val appInfo = pkgInfo.applicationInfo ?: return LoadResult.Error
        val metaData = appInfo.metaData
        val pkgName = pkgInfo.packageName

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName ?: ""
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        // Đọc libVersion từ metadata key chuẩn (tachiyomi.extension.lib.version)
        val libVersion = metaData?.getString(METADATA_LIB_VERSION)?.toDoubleOrNull()
            ?: metaData?.getFloat(METADATA_LIB_VERSION, 0f)?.takeIf { it > 0f }?.toDouble()
            ?: parseLibVersion(versionName)

        // Private APK: vẫn cần check để tránh load extension incompatible
        if (!info.isShared && (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX)) {
            logcat(LogPriority.WARN) { "Extension $extName ($pkgName, private) có libVersion $libVersion không tương thích (Yêu cầu $LIB_VERSION_MIN - $LIB_VERSION_MAX)" }
            return LoadResult.Error
        }
        
        val signatures = getSignatures(pkgInfo)
        val signatureHash = signatures.lastOrNull() ?: ""
        val trustKey = "$pkgName:$versionCode:$signatureHash"

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
            ChildFirstPathClassLoader(appInfo.sourceDir ?: "", appInfo.nativeLibraryDir, context.classLoader)
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
            .map { if (it.startsWith(".")) pkgName + it else it }
            .flatMap { className ->
                try {
                    logcat(LogPriority.DEBUG) { "Đang nạp class: $className từ $pkgName" }
                    val sourceClass = Class.forName(className, false, classLoader)
                    val obj = sourceClass.getDeclaredConstructor().newInstance()
                    when (obj) {
                        is SourceFactory -> obj.createSources()
                        is Source -> listOf(obj)
                        else -> {
                            logcat(LogPriority.ERROR) { "Class $className không phải là Source hay SourceFactory" }
                            emptyList()
                        }
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR) { "Lỗi khi khởi tạo $className từ $pkgName: ${e.message}\n${e.stackTraceToString()}" }
                    throw e
                }
            }

        if (sources.isEmpty()) return LoadResult.Error

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
            pkgFactory = metaData?.getString(METADATA_SOURCE_FACTORY),
            sources = sources,
            icon = appInfo.loadIcon(pkgManager),
            isShared = info.isShared,
            isVBook = false
        )

        return LoadResult.Success(extension)
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        val pkgName = pkgInfo.packageName
        return pkgInfo.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true ||
               pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true ||
               pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_FACTORY) == true ||
               pkgName.startsWith("eu.kanade.tachiyomi.extension.") ||
               pkgName.startsWith("mihon.extension.") ||
               pkgName.startsWith("com.example.manga_readerver2.extension.") ||
               pkgName.contains(".extension.") // Fallback broad match

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
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }

        return signatures?.map { Hash.sha256(it.toByteArray()) } ?: emptyList()
    }

    private data class ExtensionInfo(val packageInfo: PackageInfo, val isShared: Boolean)
}
