package com.example.manga_readerver2.core.download

import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Đại diện cho một tiến trình tải xuống của một chương.
 */
class Download(
    val source: Source,
    val manga: Manga,
    val chapter: Chapter,
    val isNovel: Boolean = false
) {
    var pages: List<Page>? = null

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()

    var status: State
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()

    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    @Transient
    var totalProgress: Int = 0
        get() {
            val pages = pages ?: return 0
            return pages.map { it.progress }.average().toInt()
        }

    @Transient
    var downloadedImages: Int = 0

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        COMPRESSING(3), // Trạng thái nén thành CBZ/EPUB
        DOWNLOADED(4),
        ERROR(5),
    }
}
