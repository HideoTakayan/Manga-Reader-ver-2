package com.example.manga_readerver2.features.updates

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.core.source.SourceManager
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Update
import com.example.manga_readerver2.domain.repository.MangaRepository
import com.example.manga_readerver2.core.download.Download
import com.example.manga_readerver2.core.download.DownloadCache
import com.example.manga_readerver2.core.download.DownloadManager
import com.example.manga_readerver2.core.updater.LibraryUpdateWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import android.content.Context

class UpdatesScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val context: Context = Injekt.get()
) : ScreenModel {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _updates = MutableStateFlow<List<Update>>(emptyList())
    val updates: StateFlow<List<Update>> = _updates.asStateFlow()

    // Kích hoạt trạng thái đa lựa chọn (Multi-selection Mode)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds.map { it.isNotEmpty() }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Cấu hình thời điểm đồng bộ hóa gần nhất (Last Updated Time)
    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    val downloadStatus: StateFlow<Map<Long, Download.State>> = combine(
        _updates,
        downloadManager.queueState,
        downloadCache.isInitializing
    ) { updatesList, queue, _ ->
        val map = mutableMapOf<Long, Download.State>()
        updatesList.forEach { update ->
            val download = queue.find { it.chapter.id == update.chapterId }
            if (download != null) {
                map[update.chapterId] = download.status
            } else if (downloadCache.isChapterDownloaded(update.mangaId, update.chapterName)) {
                map[update.chapterId] = Download.State.DOWNLOADED
            } else {
                map[update.chapterId] = Download.State.NOT_DOWNLOADED
            }
        }
        map
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        loadUpdates()
    }

    private fun loadUpdates() {
        screenModelScope.launch {
            _isLoading.value = true
            mangaRepository.getUpdates().collect {
                _updates.value = it
                _isLoading.value = false
                if (it.isNotEmpty()) {
                    _lastUpdated.value = System.currentTimeMillis()
                }
            }
        }
    }

    fun refresh() {
        val request = OneTimeWorkRequestBuilder<LibraryUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    // Module xử lý thao tác lựa chọn (Selection Module)─
    fun toggleSelection(chapterId: Long) {
        _selectedIds.update { current ->
            if (chapterId in current) current - chapterId else current + chapterId
        }
    }

    fun selectAll() {
        _selectedIds.value = _updates.value.map { it.chapterId }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // Module xử lý tác vụ (Action Module)─
    fun markAllRead() {
        screenModelScope.launch {
            _updates.value
                .filter { !it.read }
                .forEach { update ->
                    mangaRepository.updateChapterReadStatus(update.toChapter(read = true))
                }
        }
    }

    fun markSelectedRead(read: Boolean) {
        screenModelScope.launch {
            val selectedChapterIds = _selectedIds.value
            _updates.value
                .filter { it.chapterId in selectedChapterIds }
                .forEach { update ->
                    mangaRepository.updateChapterReadStatus(update.toChapter(read = read))
                }
            clearSelection()
        }
    }

    fun downloadSelected() {
        screenModelScope.launch {
            val selectedChapterIds = _selectedIds.value
            val toDownload = _updates.value.filter { it.chapterId in selectedChapterIds }
            toDownload.groupBy { it.mangaId }.forEach { (mangaId, items) ->
                val manga = mangaRepository.getMangaById(mangaId) ?: return@forEach
                val source = sourceManager.get(manga.source) ?: return@forEach
                val allChapters = mangaRepository.getChaptersByMangaId(mangaId)
                val chapters = allChapters.filter { ch -> items.any { it.chapterId == ch.id } }
                if (chapters.isNotEmpty()) {
                    downloadManager.downloadChapters(manga, chapters, source)
                }
            }
            clearSelection()
        }
    }

    fun deleteDownloadSelected() {
        // Trạng thái chờ (Placeholder): Yêu cầu tích hợp DownloadCache.deleteChapter để xử lý tác vụ xóa bản ghi tải xuống
        clearSelection()
    }

    fun downloadChapter(update: Update) {
        screenModelScope.launch {
            val manga = mangaRepository.getMangaById(update.mangaId) ?: return@launch
            val source = sourceManager.get(manga.source) ?: return@launch
            val chapters = mangaRepository.getChaptersByMangaId(update.mangaId)
            val chapter = chapters.find { it.id == update.chapterId } ?: return@launch
            downloadManager.downloadChapters(manga, listOf(chapter), source)
        }
    }

    private fun Update.toChapter(read: Boolean = this.read) = Chapter(
        id = chapterId,
        mangaId = mangaId,
        url = "",
        name = chapterName,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = sourceOrder
    )
}
