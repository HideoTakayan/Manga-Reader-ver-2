package com.example.manga_readerver2.features.history

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.domain.model.History
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HistoryScreenModel : ScreenModel {
    private val repository: MangaRepository = Injekt.get()

    private val _history = MutableStateFlow<List<History>>(emptyList())
    val history = _history.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        screenModelScope.launch {
            _isLoading.value = true
            repository.getHistory()
                .collect {
                    _history.value = it
                    _isLoading.value = false
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearAllHistory() {
        screenModelScope.launch {
            repository.deleteAllHistory()
        }
    }
}
