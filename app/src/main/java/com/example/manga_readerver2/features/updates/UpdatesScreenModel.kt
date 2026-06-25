package com.example.manga_readerver2.features.updates

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import com.example.manga_readerver2.domain.model.Update

class UpdatesScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get()
) : ScreenModel {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _updates = MutableStateFlow<List<Update>>(emptyList())
    val updates: StateFlow<List<Update>> = _updates.asStateFlow()

    init {
        loadUpdates()
    }

    private fun loadUpdates() {
        screenModelScope.launch {
            _isLoading.value = true
            mangaRepository.getUpdates().collect {
                _updates.value = it
                _isLoading.value = false
            }
        }
    }

    fun markAllRead() {
        screenModelScope.launch {
            _updates.value
                .filter { !it.read }
                .forEach { update ->
                    val chapter = com.example.manga_readerver2.domain.model.Chapter(
                        id = update.chapterId,
                        mangaId = update.mangaId,
                        url = "",
                        name = update.chapterName,
                        read = true,
                        bookmark = update.bookmark,
                        lastPageRead = update.lastPageRead,
                        chapterNumber = update.chapterNumber,
                        scanlator = update.scanlator,
                        dateFetch = update.dateFetch,
                        dateUpload = update.dateUpload,
                        sourceOrder = update.sourceOrder
                    )
                    mangaRepository.updateChapterReadStatus(chapter)
                }
        }
    }
}
