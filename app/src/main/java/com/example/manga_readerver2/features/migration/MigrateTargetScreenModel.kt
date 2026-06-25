package com.example.manga_readerver2.features.migration

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.core.source.SourceManager
import com.example.manga_readerver2.core.source.SourcePreferences
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import com.example.manga_readerver2.domain.usecase.MigrateMangaUseCase
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateTargetScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val migrateMangaUseCase: MigrateMangaUseCase = Injekt.get()
) : ScreenModel {

    private val _oldManga = MutableStateFlow<Manga?>(null)
    val oldManga = _oldManga.asStateFlow()

    private val _sources = MutableStateFlow<List<CatalogueSource>>(emptyList())
    val sources = _sources.asStateFlow()

    private val _selectedSource = MutableStateFlow<CatalogueSource?>(null)
    val selectedSource = _selectedSource.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Manga>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    fun init(oldMangaId: Long) {
        screenModelScope.launch(Dispatchers.IO) {
            val manga = mangaRepository.getMangaById(oldMangaId)
            _oldManga.value = manga
            
            val enabledLangs = sourcePreferences.enabledLanguages.get()
            val allSources = sourceManager.getCatalogueSources()
            
            _sources.value = allSources.filter { source ->
                enabledLangs.contains("all") || enabledLangs.contains(source.lang)
            }
        }
    }

    fun selectSource(source: CatalogueSource) {
        _selectedSource.value = source
        searchTargetManga(source)
    }

    fun clearSelectedSource() {
        _selectedSource.value = null
        _searchResults.value = emptyList()
    }

    private fun searchTargetManga(source: CatalogueSource) {
        val title = _oldManga.value?.title ?: return
        
        _isSearching.value = true
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val resultPage = source.getSearchManga(1, title, source.getFilterList())
                val mangas = resultPage.mangas.map { sManga ->
                    Manga(
                        id = -1L, // Temporary ID, will be inserted on migrate
                        source = source.id,
                        url = sManga.url,
                        title = sManga.title,
                        thumbnailUrl = sManga.thumbnail_url,
                        initialized = false
                    )
                }
                _searchResults.value = mangas
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun migrate(targetManga: Manga, copyRead: Boolean, copyCat: Boolean, deleteOld: Boolean) {
        val currentOldManga = _oldManga.value ?: return
        
        screenModelScope.launch(Dispatchers.IO) {
            // 1. Kiểm tra targetManga đã có trong thư viện chưa
            var newMangaId = mangaRepository.getMangaByUrlAndSource(targetManga.url, targetManga.source)?.id
            
            if (newMangaId == null) {
                // Thêm truyện mới vào DB nếu chưa có
                newMangaId = mangaRepository.insertManga(targetManga)
            }

            // 2. Fetch danh sách chương từ target source để đảm bảo chapters được load
            try {
                val targetSource = sourceManager.get(targetManga.source) as? CatalogueSource
                if (targetSource != null) {
                    val sManga = eu.kanade.tachiyomi.source.model.SManga.create().apply {
                        url = targetManga.url
                        title = targetManga.title
                        thumbnail_url = targetManga.thumbnailUrl
                    }
                    val networkChapters = targetSource.getChapterList(sManga)
                    
                    // Thêm các chapters vào DB (đơn giản hóa sync)
                    val newChaptersToInsert = mutableListOf<com.example.manga_readerver2.domain.model.Chapter>()
                    networkChapters.forEach { sChapter ->
                        val existingChapter = mangaRepository.getChaptersByMangaId(newMangaId)
                            .find { it.url == sChapter.url }
                        
                        if (existingChapter == null) {
                            val newChapter = com.example.manga_readerver2.domain.model.Chapter(
                                id = -1L,
                                mangaId = newMangaId,
                                url = sChapter.url,
                                name = sChapter.name,
                                dateUpload = sChapter.date_upload,
                                chapterNumber = sChapter.chapter_number,
                                scanlator = sChapter.scanlator
                            )
                            newChaptersToInsert.add(newChapter)
                        }
                    }
                    if (newChaptersToInsert.isNotEmpty()) {
                        mangaRepository.insertChapters(newChaptersToInsert)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Thực thi Migrate Use Case
            migrateMangaUseCase(
                oldMangaId = currentOldManga.id,
                newMangaId = newMangaId,
                copyReadStatus = copyRead,
                copyCategories = copyCat,
                removeOldManga = deleteOld
            )
        }
    }
}
