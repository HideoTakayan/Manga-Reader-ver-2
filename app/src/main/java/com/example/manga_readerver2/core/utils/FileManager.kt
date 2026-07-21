package com.example.manga_readerver2.core.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

class FileManager(private val context: Context) {

    companion object {
        const val ROOT_FOLDER_NAME = "MangaReader"
    }

    /**
     * Thư mục gốc: /sdcard/Android/data/com.example.manga_readerver2/files/MangaReader/
     * Được lưu trong không gian của app nên sẽ TỰ ĐỘNG BỊ XÓA khi gỡ cài đặt app.
     */
    fun getRootPath(): File {
        val storageDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(storageDir, ROOT_FOLDER_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Thư mục tải xuống: /sdcard/MangaReader/downloads/
     */
    fun getDownloadPath(): File {
        val dir = File(getRootPath(), "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
            // Ẩn nội dung khỏi các ứng dụng trình chiếu phương tiện (Media Gallery)
            File(dir, ".nomedia").createNewFile()
        }
        return dir
    }

    /**
     * Thư mục truyện local: /sdcard/MangaReader/local/
     */
    fun getLocalSourcePath(): File {
        val dir = File(getRootPath(), "local")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Thư mục backup: /sdcard/MangaReader/backups/
     */
    fun getBackupPath(): File {
        val dir = File(getRootPath(), "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMangaPath(sourceName: String, mangaTitle: String, mangaId: String): File {
        val sourceDir = File(getDownloadPath(), sanitizeFileName(sourceName))
        if (!sourceDir.exists()) sourceDir.mkdirs()
        val uniqueName = "${sanitizeFileName(mangaTitle)}_${mangaId.takeLast(6)}"
        val dir = File(sourceDir, uniqueName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getChapterPath(sourceName: String, mangaTitle: String, mangaId: String, chapterTitle: String): File {
        val dir = File(getMangaPath(sourceName, mangaTitle, mangaId), sanitizeFileName(chapterTitle))
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getChapterCbzPath(sourceName: String, mangaTitle: String, mangaId: String, chapterTitle: String): File {
        val mangaDir = getMangaPath(sourceName, mangaTitle, mangaId)
        return File(mangaDir, "${sanitizeFileName(chapterTitle)}.cbz")
    }

    fun getChapterNovelPath(sourceName: String, mangaTitle: String, mangaId: String, chapterTitle: String): File {
        val mangaDir = getMangaPath(sourceName, mangaTitle, mangaId)
        return File(mangaDir, "${sanitizeFileName(chapterTitle)}.epub")
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    fun deleteManga(sourceName: String, mangaTitle: String, mangaId: String) {
        getMangaPath(sourceName, mangaTitle, mangaId).deleteRecursively()
    }

    fun deleteChapter(sourceName: String, mangaTitle: String, mangaId: String, chapterTitle: String) {
        val cbz = getChapterCbzPath(sourceName, mangaTitle, mangaId, chapterTitle)
        if (cbz.exists()) cbz.delete()
        val epub = getChapterNovelPath(sourceName, mangaTitle, mangaId, chapterTitle)
        if (epub.exists()) epub.delete()
        val dir = getChapterPath(sourceName, mangaTitle, mangaId, chapterTitle)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun getCacheDir(): File {
        return context.cacheDir
    }

    fun formatBytes(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }

    /**
     * Kiểm tra xem app có quyền truy cập toàn bộ storage không.
     * Trên Android 11+: cần MANAGE_EXTERNAL_STORAGE
     * Trên Android 10 trở xuống: READ_EXTERNAL_STORAGE là đủ
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val permission = android.Manifest.permission.READ_EXTERNAL_STORAGE
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
