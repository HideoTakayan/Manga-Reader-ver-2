package com.example.manga_readerver2.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.mangareaderver2.database.Database
import com.example.mangareaderver2.database.Manga as DbManga
import com.example.mangareaderver2.database.Chapters as DbChapter
import com.example.mangareaderver2.database.Categories as DbCategory
import com.example.mangareaderver2.database.History as DbHistory
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.example.manga_readerver2.domain.model.Category
import com.example.manga_readerver2.domain.model.History
import com.example.mangareaderver2.database.GetHistory
import com.example.mangareaderver2.database.GetUpdates
import com.example.mangareaderver2.database.GetLibraryWithUnread
import com.example.manga_readerver2.domain.model.StatisticsData
import com.example.manga_readerver2.domain.model.SourceStat
import kotlinx.coroutines.flow.combine
import app.cash.sqldelight.coroutines.mapToOne

class MangaRepositoryImpl(
    private val database: Database
) : MangaRepository {

    private val mangaQueries = database.mangaQueries
    private val chapterQueries = database.chaptersQueries
    private val categoryQueries = database.categoriesQueries
    private val historyQueries = database.historyQueries

    override suspend fun getMangaByUrlAndSource(url: String, sourceId: Long): Manga? {
        return withContext(Dispatchers.IO) {
            mangaQueries.getMangaByUrlAndSource(url, sourceId).executeAsOneOrNull()?.let { m ->
                mapManga(m._id, m.source, m.url, m.artist, m.author, m.description, m.genre, m.title, m.status, m.thumbnail_url, m.favorite, m.last_update, m.initialized, m.viewer, m.chapter_flags, m.cover_last_modified, m.date_added)
            }
        }
    }

    override suspend fun getMangaById(id: Long): Manga? {
        return withContext(Dispatchers.IO) {
            mangaQueries.getMangaById(id).executeAsOneOrNull()?.let { m ->
                mapManga(m._id, m.source, m.url, m.artist, m.author, m.description, m.genre, m.title, m.status, m.thumbnail_url, m.favorite, m.last_update, m.initialized, m.viewer, m.chapter_flags, m.cover_last_modified, m.date_added)
            }
        }
    }

    override fun getFavorites(): Flow<List<Manga>> {
        return mangaQueries.getFavorites()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { m: DbManga ->
                    mapManga(m._id, m.source, m.url, m.artist, m.author, m.description, m.genre, m.title, m.status, m.thumbnail_url, m.favorite, m.last_update, m.initialized, m.viewer, m.chapter_flags, m.cover_last_modified, m.date_added)
                }
            }
    }

    override fun getLibrary(): Flow<List<com.example.manga_readerver2.domain.model.LibraryManga>> {
        return mangaQueries.getLibraryWithUnread()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { m: com.example.mangareaderver2.database.GetLibraryWithUnread ->
                    com.example.manga_readerver2.domain.model.LibraryManga(
                        manga = mapManga(m._id, m.source, m.url, m.artist, m.author, m.description, m.genre, m.title, m.status, m.thumbnail_url, m.favorite, m.last_update, m.initialized, m.viewer, m.chapter_flags, m.cover_last_modified, m.date_added),
                        unreadCount = m.unread_count.toInt()
                    )
                }
            }
    }

    override suspend fun insertManga(manga: Manga): Long {
        return withContext(Dispatchers.IO) {
            mangaQueries.transactionWithResult {
                val existing = mangaQueries.getMangaByUrlAndSource(manga.url, manga.source).executeAsOneOrNull()
                if (existing != null) {
                    existing._id
                } else {
                    mangaQueries.insertManga(
                        source = manga.source,
                        url = manga.url,
                        artist = manga.artist,
                        author = manga.author,
                        description = manga.description,
                        genre = manga.genre?.joinToString(", "),
                        title = manga.title,
                        status = manga.status,
                        thumbnail_url = manga.thumbnailUrl,
                        favorite = manga.favorite,
                        last_update = manga.lastUpdate,
                        initialized = manga.initialized,
                        viewer = 0L,
                        chapter_flags = 0L,
                        cover_last_modified = 0L,
                        date_added = System.currentTimeMillis()
                    )
                    mangaQueries.lastInsertRowId().executeAsOne()
                }
            }
        }
    }

    override suspend fun insertNetworkManga(manga: List<Manga>): List<Manga> {
        return withContext(Dispatchers.IO) {
            mangaQueries.transactionWithResult {
                manga.map { m ->
                    mangaQueries.insertManga(
                        source = m.source,
                        url = m.url,
                        artist = m.artist,
                        author = m.author,
                        description = m.description,
                        genre = m.genre?.joinToString(", "),
                        title = m.title,
                        status = m.status,
                        thumbnail_url = m.thumbnailUrl,
                        favorite = m.favorite,
                        last_update = m.lastUpdate,
                        initialized = m.initialized,
                        viewer = m.viewerFlags.toLong(),
                        chapter_flags = m.chapterFlags.toLong(),
                        cover_last_modified = m.coverLastModified,
                        date_added = m.dateAdded
                    )
                    var dbManga = mangaQueries.getMangaByUrlAndSource(m.url, m.source).executeAsOne()
                    
                    // Nếu manga đã tồn tại (INSERT OR IGNORE) nhưng chưa có thumbnail,
                    // Bổ sung ảnh bìa (thumbnail) nếu bộ truyện hiện tại chưa có dữ liệu này
                    if (!m.thumbnailUrl.isNullOrBlank() && (dbManga.thumbnail_url.isNullOrBlank() || dbManga.thumbnail_url != m.thumbnailUrl)) {
                        mangaQueries.updateMangaThumbnail(m.thumbnailUrl, dbManga._id)
                        dbManga = mangaQueries.getMangaByUrlAndSource(m.url, m.source).executeAsOne()
                    }

                    mapManga(
                        dbManga._id, dbManga.source, dbManga.url, dbManga.artist, dbManga.author, 
                        dbManga.description, dbManga.genre, dbManga.title, dbManga.status, 
                        dbManga.thumbnail_url, dbManga.favorite, dbManga.last_update, 
                        dbManga.initialized, dbManga.viewer, dbManga.chapter_flags, 
                        dbManga.cover_last_modified, dbManga.date_added
                    )
                }
            }
        }
    }

    override suspend fun updateManga(manga: Manga) {
        withContext<Unit>(Dispatchers.IO) {
            mangaQueries.updateManga(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre?.joinToString(", "),
                title = manga.title,
                status = manga.status,
                thumbnail_url = manga.thumbnailUrl,
                favorite = manga.favorite,
                last_update = manga.lastUpdate,
                initialized = manga.initialized,
                viewer = manga.viewerFlags.toLong(),
                chapter_flags = manga.chapterFlags.toLong(),
                cover_last_modified = manga.coverLastModified,
                _id = manga.id
            )
        }
    }

    override suspend fun updateMangaDetails(manga: Manga) {
        withContext<Unit>(Dispatchers.IO) {
            mangaQueries.updateMangaDetails(
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre?.joinToString(", "),
                status = manga.status,
                initialized = manga.initialized,
                _id = manga.id
            )
        }
    }

    override suspend fun updateMangaFavorite(id: Long, favorite: Boolean) {
        withContext<Unit>(Dispatchers.IO) {
            val dateAdded = if (favorite) System.currentTimeMillis() else 0L
            mangaQueries.updateMangaFavorite(favorite, dateAdded, id)
        }
    }

    override suspend fun deleteManga(id: Long) {
        withContext<Unit>(Dispatchers.IO) {
            mangaQueries.deleteManga(id)
        }
    }

    override suspend fun getChaptersByMangaId(mangaId: Long): List<Chapter> {
        return withContext(Dispatchers.IO) {
            chapterQueries.getChaptersByMangaId(mangaId).executeAsList().map { c: DbChapter ->
                mapChapter(c._id, c.manga_id, c.url, c.name, c.scanlator, c.read, c.bookmark, c.last_page_read, c.chapter_number, c.source_order, c.date_fetch, c.date_upload, c.last_modified_at)
            }
        }
    }

    override fun getChaptersByMangaIdAsFlow(mangaId: Long): Flow<List<Chapter>> {
        return chapterQueries.getChaptersByMangaId(mangaId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { c: DbChapter ->
                    mapChapter(c._id, c.manga_id, c.url, c.name, c.scanlator, c.read, c.bookmark, c.last_page_read, c.chapter_number, c.source_order, c.date_fetch, c.date_upload, c.last_modified_at)
                }
            }
    }

    override suspend fun insertChapters(chapters: List<Chapter>) {
        withContext<Unit>(Dispatchers.IO) {
            chapterQueries.transaction {
                chapters.forEach { chapter ->
                    chapterQueries.insertChapter(
                        manga_id = chapter.mangaId,
                        url = chapter.url,
                        name = chapter.name,
                        scanlator = chapter.scanlator,
                        read = chapter.read,
                        bookmark = chapter.bookmark,
                        last_page_read = chapter.lastPageRead,
                        chapter_number = chapter.chapterNumber,
                        source_order = chapter.sourceOrder,
                        date_fetch = System.currentTimeMillis(),
                        date_upload = chapter.dateUpload,
                        last_modified_at = System.currentTimeMillis()
                    )
                    // Luôn cập nhật metadata để đảm bảo thông tin mới nhất từ nguồn
                    chapterQueries.updateChapterMetadata(
                        name = chapter.name,
                        scanlator = chapter.scanlator,
                        chapter_number = chapter.chapterNumber,
                        source_order = chapter.sourceOrder,
                        date_upload = chapter.dateUpload,
                        last_modified_at = System.currentTimeMillis(),
                        manga_id = chapter.mangaId,
                        url = chapter.url
                    )
                }
            }
        }
    }

    override suspend fun updateChapter(chapter: Chapter) {
        withContext<Unit>(Dispatchers.IO) {
            chapterQueries.updateChapter(
                read = chapter.read,
                bookmark = chapter.bookmark,
                last_page_read = chapter.lastPageRead,
                _id = chapter.id
            )
        }
    }

    override suspend fun updateChapterReadStatus(chapter: Chapter) {
        withContext<Unit>(Dispatchers.IO) {
            chapterQueries.updateChapterReadStatusByUrl(
                read = chapter.read,
                bookmark = chapter.bookmark,
                last_page_read = chapter.lastPageRead,
                manga_id = chapter.mangaId,
                url = chapter.url
            )
        }
    }

    override suspend fun updateChapterReadStatuses(chapters: List<Chapter>) {
        withContext<Unit>(Dispatchers.IO) {
            chapterQueries.transaction {
                chapters.forEach { chapter ->
                    chapterQueries.updateChapterReadStatusByUrl(
                        read = chapter.read,
                        bookmark = chapter.bookmark,
                        last_page_read = chapter.lastPageRead,
                        manga_id = chapter.mangaId,
                        url = chapter.url
                    )
                }
            }
        }
    }

    // Categories
    override fun getCategories(): Flow<List<com.example.manga_readerver2.domain.model.Category>> {
        return categoryQueries.getCategories()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { c: DbCategory ->
                    com.example.manga_readerver2.domain.model.Category(c._id, c.name, c.sort_index, c.flags)
                }
            }
    }

    override suspend fun insertCategory(name: String, sortIndex: Long) {
        withContext<Unit>(Dispatchers.IO) {
            categoryQueries.insertCategory(name, sortIndex.toLong())
        }
    }

    override suspend fun deleteCategory(categoryId: Long) {
        withContext<Unit>(Dispatchers.IO) {
            categoryQueries.deleteCategory(categoryId)
        }
    }

    override fun getMangasInCategory(categoryId: Long): Flow<List<Manga>> {
        return categoryQueries.getMangasInCategory(categoryId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { m: DbManga ->
                    mapManga(m._id, m.source, m.url, m.artist, m.author, m.description, m.genre, m.title, m.status, m.thumbnail_url, m.favorite, m.last_update, m.initialized, m.viewer, m.chapter_flags, m.cover_last_modified, m.date_added)
                }
            }
    }

    override suspend fun addMangaToCategory(mangaId: Long, categoryId: Long) {
        withContext<Unit>(Dispatchers.IO) {
            categoryQueries.addMangaToCategory(mangaId, categoryId)
        }
    }

    override suspend fun removeMangaFromCategory(mangaId: Long, categoryId: Long) {
        withContext<Unit>(Dispatchers.IO) {
            categoryQueries.removeMangaFromCategory(mangaId, categoryId)
        }
    }

    override suspend fun removeAllCategoriesFromManga(mangaId: Long) {
        withContext<Unit>(Dispatchers.IO) {
            categoryQueries.removeAllCategoriesFromManga(mangaId)
        }
    }

    override suspend fun getMangaCategoryIds(mangaId: Long): List<Long> {
        return withContext(Dispatchers.IO) {
            categoryQueries.getMangaCategoryIds(mangaId).executeAsList()
        }
    }

    // History
    override fun getHistory(): Flow<List<com.example.manga_readerver2.domain.model.History>> {
        return historyQueries.getHistory()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { h: com.example.mangareaderver2.database.GetHistory ->
                    com.example.manga_readerver2.domain.model.History(
                        mangaId = h.mId,
                        chapterId = h.cId,
                        mangaTitle = h.mTitle,
                        chapterName = h.cName,
                        thumbnailUrl = h.tUrl,
                        sourceId = h.mSource,
                        lastRead = h.lRead
                    )
                }
            }
    }

    override suspend fun upsertHistory(chapterId: Long, lastRead: Long, timeRead: Long) {
        withContext<Unit>(Dispatchers.IO) {
            historyQueries.upsertHistory(chapterId, lastRead, timeRead)
        }
    }

    override suspend fun deleteAllHistory() {
        withContext<Unit>(Dispatchers.IO) {
            historyQueries.deleteAllHistory()
        }
    }

    override suspend fun deleteHistoryByMangaId(mangaId: Long) {
        withContext<Unit>(Dispatchers.IO) {
            historyQueries.deleteHistoryByMangaId(mangaId)
        }
    }

    override suspend fun deleteHistoryByChapterId(chapterId: Long) {
        withContext<Unit>(Dispatchers.IO) {
            historyQueries.deleteHistoryByChapterId(chapterId)
        }
    }

    override fun getUpdates(): Flow<List<com.example.manga_readerver2.domain.model.Update>> {
        return chapterQueries.getUpdates()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list ->
                list.map { u: com.example.mangareaderver2.database.GetUpdates ->
                    com.example.manga_readerver2.domain.model.Update(
                        mangaId = u.manga_id,
                        mangaTitle = u.mangaTitle,
                        chapterId = u._id,
                        chapterName = u.name,
                        scanlator = u.scanlator,
                        read = u.read,
                        bookmark = u.bookmark,
                        lastPageRead = u.last_page_read,
                        sourceOrder = u.source_order,
                        chapterNumber = u.chapter_number,
                        dateFetch = u.date_fetch,
                        dateUpload = u.date_upload,
                        lastModifiedAt = u.last_modified_at,
                        thumbnailUrl = u.mangaThumbnail
                    )
                }
            }
    }

    override fun getStats(): Flow<StatisticsData> {
        val totalMangaFlow = mangaQueries.getFavorites()
            .asFlow()
            .mapToList(Dispatchers.IO)

        val totalReadChaptersFlow = chapterQueries.getTotalReadChapters()
            .asFlow()
            .mapToOne(Dispatchers.IO)

        val totalTimeReadFlow = historyQueries.getTotalTimeRead { sum -> sum ?: 0L }
            .asFlow()
            .mapToOne(Dispatchers.IO)

        return combine(
            totalMangaFlow,
            totalReadChaptersFlow,
            totalTimeReadFlow
        ) { favorites, readChapters, timeReadMs ->
            val sourceStats = favorites.groupBy { it.source }.map { (sourceId, items) ->
                SourceStat(sourceId, items.size)
            }

            StatisticsData(
                totalManga = favorites.size,
                totalChaptersRead = readChapters.toInt(),
                totalTimeMinutes = (timeReadMs / 60000L).toInt(),
                avgPagePerMinute = 0, // Cần log thêm page count để tính
                sourceStats = sourceStats
            )
        }
    }

    // Trích xuất thống kê danh sách chương (số lượng đã đọc, bookmark) của toàn bộ thư viện bằng một truy vấn duy nhất
    override suspend fun getLibraryStats(): Map<Long, Pair<Boolean, Boolean>> = withContext(Dispatchers.IO) {
        chapterQueries.getLibraryStats()
            .executeAsList()
            .associate { row ->
                row.manga_id to Pair(
                    row.has_started == 1L,   // hasStarted: đã đọc ít nhất 1 trang
                    row.has_bookmark == 1L   // hasBookmark: có ít nhất 1 chương được bookmark
                )
            }
    }

    private fun mapManga(
        _id: Long, source: Long, url: String, artist: String?, author: String?,
        description: String?, genre: String?, title: String, status: Long,
        thumbnailUrl: String?, favorite: Boolean, last_update: Long?,
        initialized: Boolean, viewer: Long, chapter_flags: Long,
        cover_last_modified: Long?, date_added: Long?
    ): Manga = Manga(
        id = _id,
        source = source,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre?.split(", ")?.filter { it.isNotBlank() },
        status = status,
        thumbnailUrl = thumbnailUrl,
        favorite = favorite,
        lastUpdate = last_update ?: 0L,
        initialized = initialized,
        viewerFlags = viewer.toInt(),
        chapterFlags = chapter_flags.toInt(),
        coverLastModified = cover_last_modified ?: 0L,
        dateAdded = date_added ?: 0L
    )

    private fun mapChapter(
        _id: Long, manga_id: Long, url: String, name: String, scanlator: String?,
        read: Boolean, bookmark: Boolean, last_page_read: Long,
        chapter_number: Float, source_order: Long, date_fetch: Long,
        date_upload: Long, last_modified_at: Long
    ): Chapter = Chapter(
        id = _id,
        mangaId = manga_id,
        url = url,
        name = name,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = last_page_read,
        dateFetch = date_fetch,
        dateUpload = date_upload,
        chapterNumber = chapter_number,
        sourceOrder = source_order
    )
}







