package com.example.manga_readerver2.features.history

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.domain.model.History
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import com.example.manga_readerver2.core.source.SourceManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HistoryScreenModel : ScreenModel {
    private val repository: MangaRepository = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()

    private val _history = MutableStateFlow<List<History>>(emptyList())
    val history = _history.asStateFlow()

    fun isJsSource(sourceId: Long): Boolean {
        val source = sourceManager.get(sourceId)
        return source is com.example.manga_readerver2.source_js.JsSource
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    /** History đã lọc theo search query và nhóm theo ngày */
    val groupedHistory: StateFlow<Map<String, List<History>>> = combine(
        _history, _searchQuery
    ) { historyList, query ->
        val filtered = if (query.isBlank()) historyList
        else historyList.filter { it.mangaTitle.contains(query, ignoreCase = true) || it.chapterName.contains(query, ignoreCase = true) }

        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("vi"))

        filtered.groupBy { item ->
            val itemCal = Calendar.getInstance().apply { timeInMillis = item.lastRead }
            val diffDays = TimeUnit.MILLISECONDS.toDays(now.timeInMillis - item.lastRead)
            val isSameDay = now.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)
                    && now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR)
            when {
                isSameDay -> "Hôm nay"
                diffDays <= 1L -> "Hôm qua"
                diffDays < 7L -> "Tuần này"
                else -> dateFormat.format(Date(item.lastRead))
            }
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        loadHistory()
    }

    private fun loadHistory() {
        screenModelScope.launch {
            _isLoading.value = true
            repository.getHistory()
                .catch { 
                    _isLoading.value = false
                    it.printStackTrace()
                }
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

    fun deleteHistoryItem(mangaId: Long) {
        screenModelScope.launch {
            repository.deleteHistoryByMangaId(mangaId)
        }
    }
}

