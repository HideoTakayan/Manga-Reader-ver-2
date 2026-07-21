package com.example.manga_readerver2.domain.repository

import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.model.StatisticsData
import kotlinx.coroutines.flow.Flow

interface MangaRepository {

    suspend fun getMangaByUrlAndSource(url: String, sourceId: Long): Manga?

    suspend fun getMangaById(id: Long): Manga?

    fun getFavorites(): Flow<List<Manga>>
    fun getLibrary(): Flow<List<com.example.manga_readerver2.domain.model.LibraryManga>>
    suspend fun insertManga(manga: Manga): Long
    suspend fun insertNetworkManga(manga: List<Manga>): List<Manga>

    suspend fun updateManga(manga: Manga)
    suspend fun updateMangaDetails(manga: Manga)
    suspend fun updateMangaFavorite(id: Long, favorite: Boolean)

    suspend fun deleteManga(id: Long)

    // Chapters
    suspend fun getChaptersByMangaId(mangaId: Long): List<Chapter>
    fun getChaptersByMangaIdAsFlow(mangaId: Long): Flow<List<Chapter>>

    suspend fun insertChapters(chapters: List<Chapter>)

    suspend fun updateChapter(chapter: Chapter)
    suspend fun updateChapterReadStatus(chapter: Chapter)
    suspend fun updateChapterReadStatuses(chapters: List<Chapter>)

    // Categories
    fun getCategories(): Flow<List<com.example.manga_readerver2.domain.model.Category>>
    suspend fun insertCategory(name: String, sortIndex: Long)
    suspend fun deleteCategory(categoryId: Long)
    
    fun getMangasInCategory(categoryId: Long): Flow<List<Manga>>
    suspend fun addMangaToCategory(mangaId: Long, categoryId: Long)
    suspend fun removeMangaFromCategory(mangaId: Long, categoryId: Long)
    suspend fun removeAllCategoriesFromManga(mangaId: Long)
    suspend fun getMangaCategoryIds(mangaId: Long): List<Long>
    
    // History
    fun getHistory(): Flow<List<com.example.manga_readerver2.domain.model.History>>
    suspend fun upsertHistory(chapterId: Long, lastRead: Long, timeRead: Long)
    suspend fun deleteAllHistory()
    suspend fun deleteHistoryByMangaId(mangaId: Long)
    suspend fun deleteHistoryByChapterId(chapterId: Long)

    // Statistics
    fun getStats(): Flow<StatisticsData>

    // Updates
    fun getUpdates(): Flow<List<com.example.manga_readerver2.domain.model.Update>>

    // Tối ưu hoá truy vấn cơ sở dữ liệu: Lấy số liệu thống kê (hasStarted, hasBookmark) của toàn bộ thư viện thông qua một câu lệnh duy nhất
    suspend fun getLibraryStats(): Map<Long, Pair<Boolean, Boolean>>
}
