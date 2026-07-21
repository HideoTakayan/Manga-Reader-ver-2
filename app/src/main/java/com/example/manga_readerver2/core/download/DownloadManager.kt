package com.example.manga_readerver2.core.download

import android.content.Context
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import logcat.logcat
import com.example.manga_readerver2.source_js.JsSource
import kotlinx.coroutines.flow.update

class DownloadManager(
    private val context: Context,
    private val downloadStore: DownloadStore = Injekt.get()
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Hàng đợi các chương đang và sẽ tải
    private val _queueState = MutableStateFlow<List<Download>>(emptyList())
    val queueState = _queueState.asStateFlow()

    init {
        // Khôi phục hàng đợi từ file cục bộ khi khởi động app
        scope.launch {
            val restored = downloadStore.restoreQueue()
            if (restored.isNotEmpty()) {
                _queueState.value = restored
                // Tự động tiếp tục tải nếu có chương đang chờ
                if (restored.any { it.status == Download.State.QUEUE || it.status == Download.State.DOWNLOADING }) {
                    startDownloads()
                }
            }
        }
    }

    fun downloadChapters(manga: Manga, chapters: List<Chapter>, source: Source) {
        val isNovel = if (source is com.example.manga_readerver2.source_js.JsSource) source.isNovel else false
        // Loại bỏ các chương đã tồn tại trong hàng đợi để ngăn chặn tình trạng tải trùng lặp
        val existingIds = _queueState.value.map { it.chapter.id }.toSet()
        val newDownloads = chapters
            .filter { it.id !in existingIds }
            .map { chapter ->
                Download(source, manga, chapter, isNovel).apply {
                    status = Download.State.QUEUE
                }
            }
        if (newDownloads.isEmpty()) return
        
        // Cập nhật trạng thái một cách an toàn giữa các luồng (Thread-safe update)
        _queueState.update { it + newDownloads }
        
        // Lưu trữ vào Store
        scope.launch { downloadStore.saveQueue(_queueState.value) }

        // Kích hoạt dịch vụ hệ thống (Service) để thực thi tiến trình tải ngầm
        DownloadService.start(context)
    }

    fun pauseDownloads() {
        DownloadService.stop(context)
    }

    fun clearQueue() {
        DownloadService.stop(context)
        _queueState.update { emptyList() }
        scope.launch { downloadStore.clear() }
    }

    fun startDownloads() {
        if (_queueState.value.isNotEmpty()) {
            // copy sang list mới sau khi mutate để StateFlow emit được → UI update
            var hasChanged = false
            _queueState.value.forEach {
                if (it.status == Download.State.ERROR) {
                    it.status = Download.State.QUEUE
                    hasChanged = true
                }
            }
            if (hasChanged) {
                // Map tạo ra instance Download mới để đổi tham chiếu, giúp UI update state correctly
                // Không cần copy() vì UI sẽ tự động update thông qua statusFlow
            }
            DownloadService.start(context)
        }
    }

    fun retryAllFailed() {
        var hasChanged = false
        _queueState.value.forEach {
            if (it.status == Download.State.ERROR) {
                it.status = Download.State.QUEUE
                hasChanged = true
            }
        }
        if (hasChanged) {
            triggerUpdate()
            startDownloads()
        }
    }
    
    fun cancelDownload(download: Download) {
        _queueState.update { it.filter { item -> item != download } }
        scope.launch { downloadStore.saveQueue(_queueState.value) }
    }
    
    fun removeFromQueue(download: Download) {
        cancelDownload(download)
    }
    
    fun triggerUpdate() {
        _queueState.value = _queueState.value.toList()
    }
    
    internal fun updateDownloadState(download: Download, newState: Download.State) {
        download.status = newState
    }
}
