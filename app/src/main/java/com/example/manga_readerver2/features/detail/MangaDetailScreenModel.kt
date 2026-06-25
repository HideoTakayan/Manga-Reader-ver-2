package com.example.manga_readerver2.features.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import com.example.manga_readerver2.core.source.ExtensionManager
import com.example.manga_readerver2.core.download.DownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.example.manga_readerver2.core.source.SourceManager
import kotlinx.coroutines.Dispatchers

enum class ChapterSort {
    LATEST, OLDEST
}

enum class ChapterFilter {
    ALL, UNREAD, DOWNLOADED
}

class MangaDetailScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: com.example.manga_readerver2.core.download.DownloadCache = Injekt.get(),
    // Fix BUG-13: Inject qua constructor, không gọi Injekt.get() trong hot path của Flow
    private val fileManager: com.example.manga_readerver2.core.utils.FileManager = Injekt.get()
) : ScreenModel {

    private val _manga = MutableStateFlow<Manga?>(null)
    val manga: StateFlow<Manga?> = _manga.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sortMode = MutableStateFlow(ChapterSort.LATEST)
    val sortMode: StateFlow<ChapterSort> = _sortMode.asStateFlow()

    private val _filterMode = MutableStateFlow(ChapterFilter.ALL)
    val filterMode: StateFlow<ChapterFilter> = _filterMode.asStateFlow()

    private val _source = MutableStateFlow<Source?>(null)
    val source: StateFlow<Source?> = _source.asStateFlow()

    private val _allChapters = MutableStateFlow<List<Chapter>>(emptyList())
    
    val chapters: StateFlow<List<Chapter>> = combine(
        _allChapters, _sortMode, _filterMode
    ) { list, sort, filter ->
        var filtered = when (filter) {
            ChapterFilter.ALL -> list
            ChapterFilter.UNREAD -> list.filter { !it.read }
            ChapterFilter.DOWNLOADED -> {
                val manga = _manga.value
                val source = _source.value
                if (manga == null || source == null) list else {
                    val sourceName = source.name
                    list.filter { chapter ->
                        downloadCache.isChapterDownloaded(manga.id, chapter.name)
                    }
                }
            }
        }
        
        when (sort) {
            ChapterSort.LATEST -> filtered.sortedWith(compareByDescending<Chapter> { it.chapterNumber }.thenByDescending { it.dateUpload })
            ChapterSort.OLDEST -> filtered.sortedWith(compareBy<Chapter> { it.chapterNumber }.thenBy { it.dateUpload })
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    fun loadMangaDetail(mangaId: Long) {
        screenModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val m = mangaRepository.getMangaById(mangaId)

            if (m == null) {
                // Không tìm thấy truyện trong DB → thông báo lỗi rõ ràng thay vì blank screen
                _errorMessage.value = "Không tìm thấy truyện. Có thể dữ liệu đã bị xóa."
                _isLoading.value = false
                return@launch
            }

            var manga = m
            _manga.value = manga
            _isLiked.value = manga.favorite
            _source.value = sourceManager.get(manga.source)

            // Nếu chưa initialized, tải thêm thông tin từ Source
            if (!manga.initialized) {
                val source = sourceManager.get(manga.source)
                if (source != null) {
                    try {
                        val sManga = SManga.create().apply {
                            url = manga.url
                            title = manga.title
                            thumbnail_url = manga.thumbnailUrl
                        }

                        // Tải chi tiết
                        val networkManga = withContext(Dispatchers.IO) { source.getMangaDetails(sManga) }
                        manga = manga.copy(
                            author = networkManga.author ?: manga.author,
                            artist = networkManga.artist ?: manga.artist,
                            description = networkManga.description ?: manga.description,
                            genre = networkManga.genre?.split(", ")?.map { it.trim() } ?: manga.genre,
                            status = networkManga.status.toLong(),
                            initialized = true
                        )
                        mangaRepository.updateMangaDetails(manga)
                        _manga.value = manga
                    } catch (e: Exception) {
                        val errorText = "Lỗi tải thông tin truyện: ${e.message}"
                        logcat(LogPriority.ERROR) { "Error fetching manga details: ${e.message}\n${e.stackTraceToString().take(500)}" }
                        _errorMessage.value = errorText
                    }
                }
            }

            // Tải danh sách chương
            screenModelScope.launch(Dispatchers.IO) {
                val dbChapters = mangaRepository.getChaptersByMangaId(mangaId)
                
            val currentSource = _source.value
            // Nếu chưa có trong DB, tải từ mạng
            if (dbChapters.isEmpty() && currentSource != null) {
                try {
                    val sManga = SManga.create().apply {
                        url = manga.url
                        title = manga.title
                    }
                    val networkChapters: List<SChapter> = currentSource.getChapterList(sManga)
                        val chapters = networkChapters.map { networkChapter ->
                            // Use ChapterRecognition to clean up the name and extract the number
                            val recognizedNumber = com.example.manga_readerver2.core.utils.ChapterRecognition.parseChapterNumber(networkChapter.name)
                            val finalNumber = if (networkChapter.chapter_number >= 0f) networkChapter.chapter_number else recognizedNumber
                            
                            // Use the cleaned up display title if possible, else original
                            var cleanName = networkChapter.name
                            if (finalNumber >= 0f) {
                                // Try to get a cleaner display title, but keep original if the original isn't just a generic filename.
                                // If the original name looks like "Chương 1 - Hello", we might want to keep it.
                                // Actually, let's just use the recognition logic to assign a proper chapterNumber,
                                // and optionally clean the name. For now we will keep the original name but ensure number is set.
                            }

                            Chapter(
                                id = 0L,
                                mangaId = mangaId,
                                url = networkChapter.url,
                                name = networkChapter.name,
                                dateUpload = networkChapter.date_upload,
                                chapterNumber = finalNumber,
                                scanlator = networkChapter.scanlator,
                                read = false,
                                bookmark = false,
                                lastPageRead = 0L,
                                dateFetch = System.currentTimeMillis()
                            )
                        }
                        mangaRepository.insertChapters(chapters)
                    } catch (e: Exception) {
                        val errorText = "Lỗi tải danh sách chương: ${e.message}"
                        logcat(LogPriority.ERROR) { "Error fetching chapter list: ${e.message}\n${e.stackTraceToString().take(500)}" }
                        _errorMessage.value = errorText
                    }
                }
                
                _allChapters.value = mangaRepository.getChaptersByMangaId(mangaId)
            }
            _isLoading.value = false
        }
    }


    fun refreshManual() {
        screenModelScope.launch {
            _isLoading.value = true
            val m = _manga.value ?: return@launch
            val source = sourceManager.get(m.source) ?: return@launch
            
            try {
                val sManga = SManga.create().apply {
                    url = m.url
                    title = m.title
                }
                
                // Update details
                val networkManga = withContext(Dispatchers.IO) { source.getMangaDetails(sManga) }
                val updatedManga = m.copy(
                    author = networkManga.author ?: m.author,
                    artist = networkManga.artist ?: m.artist,
                    description = networkManga.description ?: m.description,
                    genre = networkManga.genre?.split(", ")?.map { it.trim() } ?: m.genre,
                    thumbnailUrl = networkManga.thumbnail_url ?: m.thumbnailUrl,
                    status = networkManga.status.toLong(),
                    initialized = true 
                )
                mangaRepository.updateManga(updatedManga)
                _manga.value = updatedManga
                
                // Update chapters
                val networkChapters = withContext(Dispatchers.IO) { source.getChapterList(sManga) }
                val chapters = networkChapters.map { sChapter ->
                    val recognizedNumber = com.example.manga_readerver2.core.utils.ChapterRecognition.parseChapterNumber(sChapter.name)
                    val finalNumber = if (sChapter.chapter_number >= 0f) sChapter.chapter_number else recognizedNumber
                    
                    com.example.manga_readerver2.domain.model.Chapter(
                        id = 0L,
                        mangaId = m.id,
                        url = sChapter.url,
                        name = sChapter.name,
                        chapterNumber = finalNumber,
                        scanlator = sChapter.scanlator,
                        read = false,
                        bookmark = false,
                        lastPageRead = 0L,
                        dateFetch = System.currentTimeMillis()
                    )
                }
                mangaRepository.insertChapters(chapters)
                _allChapters.value = mangaRepository.getChaptersByMangaId(m.id)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Lỗi làm mới dữ liệu: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun downloadChapter(chapter: com.example.manga_readerver2.domain.model.Chapter) {
        val m = _manga.value ?: return
        val source = sourceManager.get(m.source) ?: return
        downloadManager.downloadChapters(m, listOf(chapter), source)
    }

    fun downloadChapters(chapterIds: List<Long>) {
        val m = _manga.value ?: return
        val source = sourceManager.get(m.source) ?: return
        val toDownload = _allChapters.value.filter { it.id in chapterIds }
        if (toDownload.isNotEmpty()) {
            downloadManager.downloadChapters(m, toDownload, source)
        }
    }

    fun markChaptersRead(chapterIds: List<Long>, read: Boolean) {
        screenModelScope.launch(Dispatchers.IO) {
            val mangaId = _manga.value?.id ?: return@launch
            chapterIds.forEach { id ->
                val chapter = _allChapters.value.find { it.id == id } ?: return@forEach
                mangaRepository.updateChapterReadStatus(chapter.copy(read = read))
            }
            _allChapters.value = mangaRepository.getChaptersByMangaId(mangaId)
        }
    }

    fun toggleLike() {
        screenModelScope.launch {
            val currentManga = _manga.value ?: return@launch
            val newStatus = !currentManga.favorite
            mangaRepository.updateMangaFavorite(currentManga.id, newStatus)
            val updatedManga = currentManga.copy(favorite = newStatus)
            _manga.value = updatedManga
            _isLiked.value = newStatus
        }
    }

    fun setSortMode(mode: ChapterSort) {
        _sortMode.value = mode
    }

    fun setFilterMode(mode: ChapterFilter) {
        _filterMode.value = mode
    }

    val categories = mangaRepository.getCategories()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getMangaCategoryIds(): List<Long> {
        val m = _manga.value ?: return emptyList()
        return mangaRepository.getMangaCategoryIds(m.id)
    }

    fun updateMangaCategories(categoryIds: List<Long>) {
        screenModelScope.launch {
            val m = _manga.value ?: return@launch
            mangaRepository.removeAllCategoriesFromManga(m.id)
            categoryIds.forEach { catId ->
                mangaRepository.addMangaToCategory(m.id, catId)
            }
        }
    }
}
