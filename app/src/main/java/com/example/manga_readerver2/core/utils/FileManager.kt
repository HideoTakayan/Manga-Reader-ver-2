package com.example.manga_readerver2.core.utils

import android.content.Context
import android.os.Environment
import java.io.File
class FileManager(private val context: Context) {

    private val rootFolderName = "MangaReader"
    
    fun getRootPath(): File {
        // Sử dụng app-specific external storage (Android/data/com.example.manga_readerver2/files)
        // Cách này không cần xin quyền READ/WRITE trên Android 11-14 và cực kỳ ổn định.
        val dir = File(context.getExternalFilesDir(null), rootFolderName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDownloadPath(): File {
        val dir = File(getRootPath(), "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
            // Add .nomedia to hide from gallery
            File(dir, ".nomedia").createNewFile()
        }
        return dir
    }

    fun getLocalSourcePath(): File {
        val dir = File(getRootPath(), "local")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getMangaPath(sourceName: String, mangaTitle: String, mangaId: String): File {
        val sourceDir = File(getDownloadPath(), sanitizeFileName(sourceName))
        if (!sourceDir.exists()) sourceDir.mkdirs()
        
        // Sử dụng title và ID để đảm bảo tính duy nhất
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

    /**
     * Đường dẫn file .cbz cho truyện tranh (Manga).
     */
    fun getChapterCbzPath(sourceName: String, mangaTitle: String, mangaId: String, chapterTitle: String): File {
        val mangaDir = getMangaPath(sourceName, mangaTitle, mangaId)
        return File(mangaDir, "${sanitizeFileName(chapterTitle)}.cbz")
    }

    /**
     * Đường dẫn file .epub cho truyện chữ (Novel).
     */
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
}
