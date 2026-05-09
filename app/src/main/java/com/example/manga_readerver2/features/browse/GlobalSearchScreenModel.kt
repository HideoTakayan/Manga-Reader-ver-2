package com.example.manga_readerver2.features.browse

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.core.source.ExtensionManager
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import com.example.manga_readerver2.core.source.SourcePreferences

sealed class GlobalSearchResult {
    data object Loading : GlobalSearchResult()
    data class Success(val items: List<Manga>) : GlobalSearchResult()
    data class Error(val message: String) : GlobalSearchResult()
}

data class GlobalSearchState(
    val query: String = "",
    val results: Map<CatalogueSource, GlobalSearchResult> = emptyMap(),
    val isSearching: Boolean = false
)

class GlobalSearchScreenModel(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get()
) : ScreenModel {

    private val _state = MutableStateFlow(GlobalSearchState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _state.update { it.copy(query = newQuery) }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        
        // Smart Search: Detect URL
        if (query.startsWith("http://") || query.startsWith("https://")) {
            // Logic to open detail directly if source matches (advanced)
        }

        searchJob?.cancel()
        _state.update { it.copy(query = query, isSearching = true, results = emptyMap()) }

        val enabledLangs = sourcePreferences.enabledLanguages.get()
        val allSources = extensionManager.sources.value.filterIsInstance<CatalogueSource>()
        
        // VIP: Chỉ tìm trên các ngôn ngữ đã bật
        val sources = allSources.filter { source ->
            enabledLangs.contains("all") || enabledLangs.contains(source.lang)
        }
        
        // Khởi tạo trạng thái Loading cho các nguồn hợp lệ
        val initialResults = sources.associateWith { GlobalSearchResult.Loading }
        _state.update { it.copy(results = initialResults) }

        searchJob = screenModelScope.launch {
            val searchDispatcher = Dispatchers.IO.limitedParallelism(5)
            
            sources.map { source ->
                async(searchDispatcher) {
                    try {
                        val resultPage = source.fetchSearchManga(1, query, source.getFilterList())
                        val mangas = resultPage.mangas.map { sManga: eu.kanade.tachiyomi.source.model.SManga ->
                            Manga(
                                id = "${source.id}${sManga.url}".hashCode().toLong(),
                                source = source.id,
                                url = sManga.url,
                                title = sManga.title,
                                thumbnailUrl = sManga.thumbnailUrl,
                                initialized = false
                            )
                        }
                        
                        updateResult(source, GlobalSearchResult.Success(mangas))
                    } catch (e: Exception) {
                        updateResult(source, GlobalSearchResult.Error(e.message ?: "Unknown error"))
                    }
                }
            }.awaitAll()
            
            _state.update { it.copy(isSearching = false) }
        }
    }

    suspend fun getMangaId(manga: Manga): Long {
        return withContext(Dispatchers.IO) {
            val existing = mangaRepository.getMangaByUrlAndSource(manga.url, manga.source)
            if (existing != null) {
                existing.id
            } else {
                mangaRepository.insertManga(manga)
            }
        }
    }

    private fun updateResult(source: CatalogueSource, result: GlobalSearchResult) {
        _state.update { currentState ->
            val newResults = currentState.results.toMutableMap()
            newResults[source] = result
            currentState.copy(results = newResults)
        }
    }
}
