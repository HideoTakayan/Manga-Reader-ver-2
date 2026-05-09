package com.example.manga_readerver2.core.download

import android.content.Context
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import com.example.manga_readerver2.core.source.ExtensionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Lưu trữ trạng thái hàng đợi tải xuống vào một file JSON cục bộ.
 * Giúp khôi phục lại các tiến trình tải khi ứng dụng bị đóng hoặc khởi động lại.
 */
class DownloadStore(
    private val context: Context,
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get()
) {
    private val storeFile = File(context.filesDir, "download_queue.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class DownloadObject(
        val mangaId: Long,
        val chapterId: Long,
        val sourceId: Long
    )

    suspend fun saveQueue(downloads: List<Download>) = withContext(Dispatchers.IO) {
        try {
            val list = downloads.map {
                DownloadObject(it.manga.id, it.chapter.id, it.source.id)
            }
            val content = json.encodeToString(list)
            storeFile.writeText(content)
        } catch (e: Exception) {
            logcat { "Failed to save download queue: ${e.message}" }
        }
    }

    suspend fun restoreQueue(): List<Download> = withContext(Dispatchers.IO) {
        if (!storeFile.exists()) return@withContext emptyList()

        try {
            val content = storeFile.readText()
            val list = json.decodeFromString<List<DownloadObject>>(content)
            
            val result = mutableListOf<Download>()
            for (obj in list) {
                val manga = mangaRepository.getMangaById(obj.mangaId) ?: continue
                val chapters = mangaRepository.getChaptersByMangaId(obj.mangaId)
                val chapter = chapters.find { it.id == obj.chapterId } ?: continue
                val source = extensionManager.getSource(obj.sourceId) ?: continue
                
                result.add(Download(source, manga, chapter))
            }
            return@withContext result
        } catch (e: Exception) {
            logcat { "Failed to restore download queue: ${e.message}" }
            emptyList()
        }
    }
    
    suspend fun clear() = withContext(Dispatchers.IO) {
        if (storeFile.exists()) {
            storeFile.delete()
        }
    }
}
