package com.example.manga_readerver2.core.download

import android.content.Context
import com.example.manga_readerver2.core.utils.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * DownloadCache tracks downloaded chapters in memory to avoid expensive disk I/O when checking status.
 */
class DownloadCache(
    private val context: Context,
    private val fileManager: FileManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // mangaId -> Set of sanitized chapter names
    private val rootDownloads = ConcurrentHashMap<Long, MutableSet<String>>()

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing = _isInitializing.asStateFlow()

    init {
        scope.launch {
            // Load cache on startup
            val downloadDir = fileManager.getDownloadPath()
            if (downloadDir.exists()) {
                downloadDir.listFiles()?.forEach { sourceDir ->
                    if (sourceDir.isDirectory) {
                        sourceDir.listFiles()?.forEach { mangaDir ->
                            if (mangaDir.isDirectory) {
                                val parts = mangaDir.name.split("_")
                                if (parts.size >= 2) {
                                    val mangaIdStr = parts.last()
                                    val mangaId = mangaIdStr.toLongOrNull()
                                    if (mangaId != null) {
                                        val chapterFiles = mangaDir.listFiles()
                                            ?.map { it.nameWithoutExtension }
                                            ?.toMutableSet()
                                        if (chapterFiles != null) {
                                            rootDownloads[mangaId] = chapterFiles
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            _isInitializing.value = false
        }
    }

    /**
     * Checks if a chapter is downloaded.
     */
    fun isChapterDownloaded(mangaId: Long, chapterName: String): Boolean {
        val sanitized = sanitizeFileName(chapterName)
        val mangaDownloads = rootDownloads[mangaId]
        return mangaDownloads?.contains(sanitized) == true
    }

    fun addChapter(mangaId: Long, chapterName: String) {
        val sanitized = sanitizeFileName(chapterName)
        val set = rootDownloads.getOrPut(mangaId) { ConcurrentHashMap.newKeySet() }
        set.add(sanitized)
    }

    fun removeChapter(mangaId: Long, chapterName: String) {
        val sanitized = sanitizeFileName(chapterName)
        rootDownloads[mangaId]?.remove(sanitized)
    }

    fun removeManga(mangaId: Long) {
        rootDownloads.remove(mangaId)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
