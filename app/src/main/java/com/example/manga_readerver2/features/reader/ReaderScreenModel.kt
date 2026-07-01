package com.example.manga_readerver2.features.reader

import android.content.Context
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.core.utils.ArchiveReader
import com.example.manga_readerver2.core.utils.FileManager
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import com.example.manga_readerver2.core.preference.ReaderPreferences
import com.example.manga_readerver2.core.source.ExtensionManager
import com.example.manga_readerver2.core.source.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import com.example.manga_readerver2.core.utils.HtmlParser
import com.example.manga_readerver2.core.utils.ReaderBlock

sealed class ReaderPage {
    open var chapter: Chapter? = null

    data class Local(val file: File) : ReaderPage()
    data class Archive(val file: File, val entryName: String) : ReaderPage()
    data class Online(var url: String, val index: Int, var isLoading: Boolean = false, var localFile: String? = null, var hasError: Boolean = false, var pageUrl: String = "", var originalPage: eu.kanade.tachiyomi.source.model.Page? = null) : ReaderPage()
    data class Text(val content: String) : ReaderPage()
    data class Pdf(val file: File, val pageIndex: Int) : ReaderPage()
    data class Transition(val currChapter: Chapter, val nextChapter: Chapter?) : ReaderPage()
}

enum class ReadingMode {
    RIGHT_TO_LEFT, LEFT_TO_RIGHT, VERTICAL, WEBTOON
}

class ReaderScreenModel(
    private val context: Context = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val fileManager: FileManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val displayPreferences: com.example.manga_readerver2.core.preference.DisplayPreferences = Injekt.get(),
    private val downloadPreferences: com.example.manga_readerver2.core.preference.DownloadPreferences = Injekt.get(),
    private val generalPreferences: com.example.manga_readerver2.core.preference.GeneralPreferences = Injekt.get(),
    private val anilistManager: com.example.manga_readerver2.core.track.AniListManager = Injekt.get(),
    private val ttsManager: com.example.manga_readerver2.core.tts.TtsManager = Injekt.get(),
    private val downloadManager: com.example.manga_readerver2.core.download.DownloadManager = Injekt.get(),
    private val sourceManager: com.example.manga_readerver2.core.source.SourceManager = Injekt.get()
) : ScreenModel {
    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // TTS States
    val isTtsPlaying = ttsManager.isPlaying
    val currentTtsParagraph = ttsManager.currentParagraphIndex
    val availableVoices = ttsManager.availableVoices
    val selectedVoice = ttsManager.selectedVoice

    fun setTtsVoice(voice: android.speech.tts.Voice) {
        ttsManager.setVoice(voice)
    }

    private val _chapterName = MutableStateFlow("")
    val chapterName: StateFlow<String> = _chapterName.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()
    
    private val _ttsSpeed = MutableStateFlow(readerPreferences.ttsSpeechRate.get())
    val ttsSpeed = _ttsSpeed.asStateFlow()
    
    private val _ttsPitch = MutableStateFlow(readerPreferences.ttsPitch.get())
    val ttsPitch = _ttsPitch.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(readerPreferences.keepScreenOn.get())
    val keepScreenOn = _keepScreenOn.asStateFlow()

    private val _fullscreen = MutableStateFlow(readerPreferences.fullscreen.get())
    val fullscreen = _fullscreen.asStateFlow()

    private val _colorFilterMode = MutableStateFlow(readerPreferences.colorFilterMode.get())
    val colorFilterMode = _colorFilterMode.asStateFlow()

    private val _scaleMode = MutableStateFlow(0) // 0: Fit Screen, 1: Fit Width, 2: Fit Height
    val scaleMode = _scaleMode.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _incognitoMode = MutableStateFlow(generalPreferences.incognitoMode.get())
    val incognitoMode = _incognitoMode.asStateFlow()

    private val _autoDownloadAmount = MutableStateFlow(readerPreferences.autoDownloadAmount.get())
    val autoDownloadAmount = _autoDownloadAmount.asStateFlow()

    private val _readingMode = MutableStateFlow(ReadingMode.entries.getOrElse(readerPreferences.readingMode.get()) { ReadingMode.VERTICAL })
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    private val _orientation = MutableStateFlow(0) // 0: Auto, 1: Portrait, 2: Landscape
    val orientation = _orientation.asStateFlow()

    private val _readerTheme = MutableStateFlow(readerPreferences.theme.get()) // 0: Dark, 1: White, 2: Sepia
    val readerTheme = _readerTheme.asStateFlow()

    private val _cropBorders = MutableStateFlow(readerPreferences.cropBorders.get())
    val cropBorders = _cropBorders.asStateFlow()

    private val _brightness = MutableStateFlow(1.0f) // 0.0 to 1.0 (1.0 is full bright)
    val brightness = _brightness.asStateFlow()

    private val _customColorFilter = MutableStateFlow(readerPreferences.customColorFilter.get())
    val customColorFilter = _customColorFilter.asStateFlow()

    private val _customColorFilterColor = MutableStateFlow(readerPreferences.customColorFilterColor.get())
    val customColorFilterColor = _customColorFilterColor.asStateFlow()

    private val _customColorFilterAlpha = MutableStateFlow(readerPreferences.customColorFilterAlpha.get())
    val customColorFilterAlpha = _customColorFilterAlpha.asStateFlow()

    private val _customColorFilterBlendMode = MutableStateFlow(readerPreferences.customColorFilterBlendMode.get())
    val customColorFilterBlendMode = _customColorFilterBlendMode.asStateFlow()

    private val _invertColors = MutableStateFlow(readerPreferences.invertColors.get())
    val invertColors = _invertColors.asStateFlow()

    private val _grayscale = MutableStateFlow(readerPreferences.grayscale.get())
    val grayscale = _grayscale.asStateFlow()

    private val _dualPage = MutableStateFlow(readerPreferences.dualPage.get())
    val dualPage = _dualPage.asStateFlow()

    private val _webtoonSidePadding = MutableStateFlow(readerPreferences.webtoonSidePadding.get())
    val webtoonSidePadding = _webtoonSidePadding.asStateFlow()

    private val _fontSize = MutableStateFlow(readerPreferences.fontSize.get())
    val fontSize = _fontSize.asStateFlow()

    private val _autoScrollSpeed = MutableStateFlow(readerPreferences.autoScrollSpeed.get())
    val autoScrollSpeed = _autoScrollSpeed.asStateFlow()

    private val _lineSpacing = MutableStateFlow(readerPreferences.lineSpacing.get())
    val lineSpacing = _lineSpacing.asStateFlow()

    private val _customTapZones = MutableStateFlow(
        readerPreferences.customTapZones.get().split(",").mapNotNull {
            val ordinal = it.toIntOrNull()
            if (ordinal != null && ordinal in 0 until ReaderPreferences.TapAction.entries.size) {
                ReaderPreferences.TapAction.entries[ordinal]
            } else {
                ReaderPreferences.TapAction.NONE
            }
        }.let {
            if (it.size == 9) it else List(9) { i ->
                when (i % 3) {
                    0 -> ReaderPreferences.TapAction.PREVIOUS
                    1 -> ReaderPreferences.TapAction.MENU
                    else -> ReaderPreferences.TapAction.NEXT
                }
            }
        }
    )
    val customTapZones = _customTapZones.asStateFlow()


    private val _allPages = MutableStateFlow<List<ReaderPage>>(emptyList())
    val allPages: StateFlow<List<ReaderPage>> = _allPages.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isTextReader = MutableStateFlow(false)
    val isTextReader: StateFlow<Boolean> = _isTextReader.asStateFlow()

    private val _textBlocks = MutableStateFlow<List<ReaderBlock>>(emptyList())
    val textBlocks: StateFlow<List<ReaderBlock>> = _textBlocks.asStateFlow()

    private var mangaId: Long = 0L
    private var chapterId: Long = 0L
    private var manga: Manga? = null
    var currentChapter: Chapter? = null
        private set
    private var currentMangaChapters: List<Chapter> = emptyList()
    private var startTime: Long = 0L
    private val zipFiles = mutableMapOf<String, java.util.zip.ZipFile>()
    private val pdfRenderers = mutableMapOf<String, android.graphics.pdf.PdfRenderer>()
    private val pdfFileDescriptors = mutableMapOf<String, android.os.ParcelFileDescriptor>()
    // Track page load jobs để cancel khi chuyển chapter tránh hiển thị ảnh sai
    private val pageLoadJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    private val zipMutex = kotlinx.coroutines.sync.Mutex()
    private var chapterLoadJob: kotlinx.coroutines.Job? = null

    init {
        // Collect reactive preferences to update UI state
        screenModelScope.launch {
            readerPreferences.readingMode.asFlow().collect { modeInt ->
                val mode = when (modeInt) {
                    0 -> ReadingMode.RIGHT_TO_LEFT // Old HORIZONTAL was 0 -> mapped to RTL
                    1 -> ReadingMode.WEBTOON // Old VERTICAL was 1 -> mapped to WEBTOON
                    else -> ReadingMode.entries.getOrElse(modeInt) { ReadingMode.WEBTOON }
                }
                _readingMode.value = mode
            }
        }
        screenModelScope.launch {
            readerPreferences.keepScreenOn.asFlow().collect { _keepScreenOn.value = it }
        }
        screenModelScope.launch {
            readerPreferences.fullscreen.asFlow().collect { _fullscreen.value = it }
        }
        screenModelScope.launch {
            readerPreferences.colorFilterMode.asFlow().collect { _colorFilterMode.value = it }
        }
        screenModelScope.launch {
            readerPreferences.ttsSpeechRate.asFlow().collect { _ttsSpeed.value = it }
        }
        screenModelScope.launch {
            readerPreferences.ttsPitch.asFlow().collect { _ttsPitch.value = it }
        }
        screenModelScope.launch {
            generalPreferences.incognitoMode.asFlow().collect { _incognitoMode.value = it }
        }
        screenModelScope.launch {
            readerPreferences.autoDownloadAmount.asFlow().collect { _autoDownloadAmount.value = it }
        }
        screenModelScope.launch {
            readerPreferences.cropBorders.asFlow().collect { _cropBorders.value = it }
        }
        
        // Fix BUG-07: Tự động chuyển chương khi TTS đọc hết
        screenModelScope.launch {
            ttsManager.onComplete.collect {
                loadNextChapter()
            }
        }
    }

    fun loadNextChapter() {
        val current = currentChapter ?: return
        val sorted = currentMangaChapters.sortedBy { it.chapterNumber }
        val index = sorted.indexOfFirst { it.id == current.id }
        if (index != -1 && index < sorted.size - 1) {
            val next = sorted[index + 1]
            loadChapter(mangaId, next.id)
        }
    }

    fun loadPrevChapter() {
        val current = currentChapter ?: return
        val sorted = currentMangaChapters.sortedBy { it.chapterNumber }
        val index = sorted.indexOfFirst { it.id == current.id }
        if (index > 0) {
            val prev = sorted[index - 1]
            loadChapter(mangaId, prev.id)
        }
    }
    private val appendingChapters = mutableSetOf<Long>()

    fun appendNextChapter() {
        val manga = manga ?: return
        val sortedChapters = currentMangaChapters.sortedBy { it.chapterNumber }
        val lastTransition = _allPages.value.lastOrNull { it is ReaderPage.Transition } as? ReaderPage.Transition
        val nextChapter = lastTransition?.nextChapter ?: return
        
        // Prevent duplicate appending
        if (_allPages.value.any { it.chapter?.id == nextChapter.id && it !is ReaderPage.Transition }) return
        if (appendingChapters.contains(nextChapter.id)) return
        appendingChapters.add(nextChapter.id)

        screenModelScope.launch {
            try {
                val pages = fetchChapterPages(manga, nextChapter)
                pages.forEach { it.chapter = nextChapter }
                
                val currentIndex = sortedChapters.indexOfFirst { it.id == nextChapter.id }
                val nextNextChapter = if (currentIndex >= 0) sortedChapters.getOrNull(currentIndex + 1) else null
                
                val transition = ReaderPage.Transition(nextChapter, nextNextChapter)
                transition.chapter = nextChapter
                
                _allPages.value = _allPages.value + pages + listOf(transition)
                _pageCount.value = _allPages.value.size

                // For text reader, we don't automatically merge the next chapter's text blocks
                // because it causes the next chapter to immediately appear below the current one.
                // The user will use the next/prev buttons to navigate text chapters.
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                appendingChapters.remove(nextChapter.id)
            }
        }
    }

    fun onPageChanged(index: Int) {
        val pages = _allPages.value
        if (index !in pages.indices) return
        val page = pages[index]
        
        _currentPage.value = index

        val pageChapter = page.chapter
        if (pageChapter != null && pageChapter.id != chapterId) {
            // User scrolled into a new chapter!
            this.chapterId = pageChapter.id
            this.currentChapter = pageChapter
            _chapterName.value = pageChapter.name
            _isChapterBookmarked.value = pageChapter.bookmark
            
            // If they reached the new chapter, we should preload the NEXT chapter!
            appendNextChapter()
        }

        saveHistory()
    }

    fun goToNextPage() {
        if (_currentPage.value < _pageCount.value - 1) {
            setPage(_currentPage.value + 1)
        }
    }

    fun goToPrevPage() {
        if (_currentPage.value > 0) {
            setPage(_currentPage.value - 1)
        }
    }

    fun loadChapter(mId: Long, cId: Long) {
        this.mangaId = mId
        this.chapterId = cId
        this.startTime = System.currentTimeMillis()
        _isLoading.value = true
        _errorMessage.value = null
        _isTextReader.value = false
        
        // Stop TTS when loading new chapter
        ttsManager.stop()

        // Cancel tất cả page load jobs của chapter cũ để tránh ghi đè dữ liệu chapter mới
        pageLoadJobs.values.forEach { it.cancel() }
        pageLoadJobs.clear()

        // Fix R5: Dọn dẹp cache online ảnh của chapter cũ trước khi load chapter mới
        screenModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.listFiles()?.filter { 
                    it.name.startsWith("online_") 
                }?.forEach { it.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        screenModelScope.launch {
            try {
                manga = mangaRepository.getMangaById(mId)
                val allChapters = mangaRepository.getChaptersByMangaId(mId)
                _chapters.value = allChapters
                currentMangaChapters = allChapters
                currentChapter = allChapters.find { it.id == cId }
                _isChapterBookmarked.value = currentChapter?.bookmark ?: false

                val manga = manga ?: throw Exception("Không tìm thấy truyện")
                val currentChapter = currentChapter ?: throw Exception("Không tìm thấy chương")
                
                _chapterName.value = currentChapter.name
                
                val pages = fetchChapterPages(manga, currentChapter)
                pages.forEach { it.chapter = currentChapter }
                
                val sortedChapters = currentMangaChapters.sortedBy { it.chapterNumber }
                val currentIndex = sortedChapters.indexOfFirst { it.id == cId }
                val nextChapter = if (currentIndex >= 0) sortedChapters.getOrNull(currentIndex + 1) else null
                
                val transition = ReaderPage.Transition(currentChapter, nextChapter)
                transition.chapter = currentChapter
                
                _allPages.value = pages + listOf(transition)
                _pageCount.value = _allPages.value.size
                
                // Parse text blocks if it is a text reader
                val firstPage = pages.firstOrNull()
                if (firstPage is ReaderPage.Text) {
                    _textBlocks.value = HtmlParser.parseToBlocks(firstPage.content, currentChapter?.url ?: "")
                }
                
                // Guard: nếu pages rỗng thì không coerceIn vì pages.size - 1 = -1 → crash
                _currentPage.value = if (pages.isNotEmpty()) {
                    currentChapter.lastPageRead.toInt().coerceIn(0, pages.size - 1)
                } else 0
                _isLoading.value = false
                saveHistory()
                
                // Preload the next chapter seamlessly
                appendNextChapter()
            } catch (e: Throwable) {
                _errorMessage.value = e.message ?: "Lỗi tải chương"
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchChapterPages(manga: Manga, chapter: Chapter): List<ReaderPage> {
        val sourceName = sourceManager.get(manga.source)?.name ?: "Unknown"
        val isDownloading = downloadManager.queueState.value.any { 
            it.manga.id == manga.id && it.chapter.id == chapter.id && it.status == com.example.manga_readerver2.core.download.Download.State.DOWNLOADING 
        }
        
        if (!isDownloading) {
            // Local/Imported
            if (chapter.url.startsWith("local/")) {
                val localFileName = chapter.url.substringAfter("local/").substringBefore("#")
                val sourceNameForPath = if (manga.source == 0L) "Local" else sourceName
                val localFile = File(fileManager.getMangaPath(sourceNameForPath, manga.title, manga.id.toString()), localFileName)
                
                if (localFile.exists()) {
                    val extension = localFile.extension.lowercase()
                    if (extension == "cbz") {
                        zipFiles[localFile.absolutePath] = withContext(Dispatchers.IO) { java.util.zip.ZipFile(localFile) }
                        val entries = com.example.manga_readerver2.core.utils.ArchiveReader.getOrderedImageEntries(localFile)
                        return entries.map { ReaderPage.Archive(localFile, it) }
                    } else if (extension == "pdf") {
                        val pfd = android.os.ParcelFileDescriptor.open(localFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        pdfFileDescriptors[localFile.absolutePath] = pfd
                        pdfRenderers[localFile.absolutePath] = renderer
                        return (0 until renderer.pageCount).map { ReaderPage.Pdf(localFile, it) }
                    } else if (extension == "epub") {
                        _isTextReader.value = true
                        val href = chapter.url.substringAfter("#", "")
                        val content = withContext(Dispatchers.IO) { 
                            if (href.isNotEmpty()) {
                                com.example.manga_readerver2.core.utils.EpubReader.getChapterText(localFile, href)
                            } else {
                                com.example.manga_readerver2.core.utils.EpubReader.getFullText(localFile) 
                            }
                        }
                        return listOf(ReaderPage.Text(content))
                    }
                }
            } else {
                // Online chapter or Downloaded online chapter
                val cbzFile = fileManager.getChapterCbzPath(sourceName, manga.title, manga.id.toString(), chapter.name)
                val epubFile = fileManager.getChapterNovelPath(sourceName, manga.title, manga.id.toString(), chapter.name)
                
                if (cbzFile.exists()) {
                    zipFiles[cbzFile.absolutePath] = withContext(Dispatchers.IO) { java.util.zip.ZipFile(cbzFile) }
                    val entries = com.example.manga_readerver2.core.utils.ArchiveReader.getOrderedImageEntries(cbzFile)
                    return entries.map { ReaderPage.Archive(cbzFile, it) }
                } else if (epubFile.exists()) {
                    _isTextReader.value = true
                    val content = withContext(Dispatchers.IO) { 
                        com.example.manga_readerver2.core.utils.EpubReader.getFullText(epubFile)
                    }
                    return listOf(ReaderPage.Text(content))
                } else {
                    // Not downloaded → fetch online (ném exception để caller hiển thị lỗi)
                    val source = sourceManager.get(manga.source)
                        ?: throw Exception("Không tìm thấy nguồn (source id=${manga.source}). Extension có thể chưa được cài.")
                    val sChapter = eu.kanade.tachiyomi.source.model.SChapter.create().apply {
                        url = chapter.url
                        name = chapter.name
                    }
                    val pageList = withContext(Dispatchers.IO) {
                        source.getPageList(sChapter)
                    }
                    if (pageList.isEmpty()) throw Exception("Nguồn trả về danh sách trang rỗng.")
                    
                    val firstPageUrl = pageList.first().imageUrl ?: ""
                    if (firstPageUrl.startsWith("vbook-text://")) {
                        _isTextReader.value = true
                        val textContent = firstPageUrl.removePrefix("vbook-text://")
                        return listOf(ReaderPage.Text(textContent))
                    }

                    return pageList.mapIndexed { index, page ->
                        ReaderPage.Online(
                            url = page.imageUrl ?: "",
                            index = index,
                            pageUrl = page.url,
                            originalPage = page
                        )
                    }
                }
            }
        }
        return emptyList()
    }

    // Fix R2: Thêm suspend parameter để sử dụng được với mutex (vì zipMutex là coroutine mutex)
    // Hoặc sửa thành synchronized lock nếu không muốn suspend.
    // Vì ArchiveReader.getPageStream không suspend, ta dùng synchronized object cho đơn giản và hiệu quả.
    private val zipLock = Any()
    private val pdfLock = Any()
    
    fun getPageBytes(page: ReaderPage.Archive): ByteArray? {
        return try {
            synchronized(zipLock) {
                val zip = zipFiles[page.file.absolutePath] ?: return@synchronized null
                ArchiveReader.getPageStream(zip, page.entryName)?.readBytes()
            }
        } catch (e: Throwable) {
            // Zip đã bị đóng (do chuyển chapter) → trả null, UI hiện loading rồi tự retry
            null
        }
    }

    fun renderPdfPage(page: ReaderPage.Pdf): String? {
        return try {
            // PdfRenderer không thread-safe → dùng synchronized lock
            synchronized(pdfLock) {
                val renderer = pdfRenderers[page.file.absolutePath] ?: return@synchronized null
                val cacheFile = File(context.cacheDir, "pdf_${page.file.absolutePath.hashCode()}_${page.pageIndex}.jpg")
                if (cacheFile.exists()) return@synchronized cacheFile.absolutePath

                // Kiểm tra pageIndex hợp lệ để tránh IllegalArgumentException
                if (page.pageIndex < 0 || page.pageIndex >= renderer.pageCount) return@synchronized null

                val pdfPage = renderer.openPage(page.pageIndex)

                // Scale theo screen width, tối đa x2 để tránh OOM
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val scale = (screenWidth.toFloat() / pdfPage.width.coerceAtLeast(1)).coerceAtMost(2.0f)
                val bitmapWidth = (pdfPage.width * scale).toInt().coerceAtLeast(1)
                val bitmapHeight = (pdfPage.height * scale).toInt().coerceAtLeast(1)

                val bitmap = android.graphics.Bitmap.createBitmap(
                    bitmapWidth, bitmapHeight,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                // Vẽ nền trắng trước (PDF trang trắng không có nền)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                pdfPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pdfPage.close()

                cacheFile.outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                }
                bitmap.recycle()
                cacheFile.absolutePath
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun setPage(index: Int) {
        if (index in 0 until _pageCount.value) {
            _currentPage.value = index
            onPageChanged(index)
            // Tải trước nếu ở gần cuối chương (giữ nguyên logic prefetch online)
            if (index > _pageCount.value * 0.8) {
                downloadNextChapters()
            }
        }
    }

    private var lastSavedPageIndex = -1
    private var lastSavedTime = 0L

    private fun saveHistory() {
        if (_incognitoMode.value) return
        
        val pages = _allPages.value
        val index = _currentPage.value
        if (index !in pages.indices) return
        val page = pages[index]
        
        val cId = page.chapter?.id ?: chapterId
        val chapter = page.chapter ?: currentChapter
        
        val currentChapterPages = pages.filter { it.chapter?.id == cId && it !is ReaderPage.Transition }
        val localIndex = currentChapterPages.indexOf(page)
        
        // If it's a transition page, don't update progress
        if (localIndex < 0) return
        
        val totalPages = currentChapterPages.size
        
        // Tránh lưu liên tục nếu dữ liệu không đổi đáng kể
        val now = System.currentTimeMillis()
        val timeDiff = now - lastSavedTime
        if (localIndex == lastSavedPageIndex && timeDiff < 5000) return 

        // BUG-3 fix: Dùng screenModelScope thay vì GlobalScope
        screenModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                // Cập nhật lịch sử đọc
                val timeRead = if (startTime > 0) now - startTime else 0L
                if (timeRead > 0 || localIndex != lastSavedPageIndex) {
                    mangaRepository.upsertHistory(cId, now, timeRead)
                    startTime = now
                    lastSavedTime = now
                    lastSavedPageIndex = localIndex
                }

                // Cập nhật trang cuối cùng trong bản ghi chapter
                if (localIndex >= 0 && chapter != null) {
                    val isRead = totalPages > 0 && localIndex >= totalPages - 1
                    mangaRepository.updateChapter(chapter.copy(
                        lastPageRead = localIndex.toLong(),
                        read = if (isRead) true else chapter.read
                    ))
                    
                    if (isRead) {
                        manga?.let { m ->
                            anilistManager.updateProgress(m.title, chapter.chapterNumber.toInt())
                        }
                    }
                    
                    if (isRead && downloadPreferences.autoDeleteAfterReading.get()) {
                        val manga = mangaRepository.getMangaById(mangaId)
                        if (manga != null) {
                            val sourceName = sourceManager.get(manga.source)?.name ?: manga.source.toString()
                            fileManager.deleteChapter(sourceName, manga.title, manga.id.toString(), chapter.name)
                            val downloadCache = Injekt.get<com.example.manga_readerver2.core.download.DownloadCache>()
                            downloadCache.removeChapter(manga.id, chapter.name)
                        }
                    }
                }
            }
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        readerPreferences.readingMode.set(mode.ordinal)
    }

    fun setReadingModeTemp(mode: ReadingMode) {
        _readingMode.value = mode
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        readerPreferences.keepScreenOn.set(enabled)
    }

    fun setFullscreen(enabled: Boolean) {
        _fullscreen.value = enabled
        readerPreferences.fullscreen.set(enabled)
    }

    fun setColorFilterMode(mode: Int) {
        _colorFilterMode.value = mode
        readerPreferences.colorFilterMode.set(mode)
    }

    fun setScaleMode(mode: Int) {
        _scaleMode.value = mode
    }

    fun setIncognitoMode(enabled: Boolean) {
        _incognitoMode.value = enabled
        generalPreferences.incognitoMode.set(enabled)
    }

    fun setAutoDownloadAmount(amount: Int) {
        _autoDownloadAmount.value = amount
        readerPreferences.autoDownloadAmount.set(amount)
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        readerPreferences.fontSize.set(size)
    }

    fun setAutoScrollSpeed(speed: Float) {
        _autoScrollSpeed.value = speed
        readerPreferences.autoScrollSpeed.set(speed)
    }

    fun setLineSpacing(spacing: Float) {
        _lineSpacing.value = spacing
        readerPreferences.lineSpacing.set(spacing)
    }

    fun setOrientation(value: Int) {
        _orientation.value = value
    }

    fun setReaderTheme(theme: Int) {
        _readerTheme.value = theme
        readerPreferences.theme.set(theme)
    }

    fun setCropBorders(crop: Boolean) {
        _cropBorders.value = crop
        readerPreferences.cropBorders.set(crop)
    }

    fun setCustomColorFilter(enabled: Boolean) {
        _customColorFilter.value = enabled
        readerPreferences.customColorFilter.set(enabled)
    }

    fun setCustomColorFilterColor(color: Int) {
        _customColorFilterColor.value = color
        readerPreferences.customColorFilterColor.set(color)
    }

    fun setCustomColorFilterAlpha(alpha: Float) {
        _customColorFilterAlpha.value = alpha
        readerPreferences.customColorFilterAlpha.set(alpha)
    }

    fun setCustomColorFilterBlendMode(mode: Int) {
        _customColorFilterBlendMode.value = mode
        readerPreferences.customColorFilterBlendMode.set(mode)
    }

    fun setInvertColors(enabled: Boolean) {
        _invertColors.value = enabled
        readerPreferences.invertColors.set(enabled)
    }

    fun setGrayscale(enabled: Boolean) {
        _grayscale.value = enabled
        readerPreferences.grayscale.set(enabled)
    }

    fun setDualPage(enabled: Boolean) {
        _dualPage.value = enabled
        readerPreferences.dualPage.set(enabled)
    }

    fun setWebtoonSidePadding(padding: Int) {
        _webtoonSidePadding.value = padding
        readerPreferences.webtoonSidePadding.set(padding)
    }

    fun setBrightness(value: Float) {
        _brightness.value = value
    }

    private val _isChapterBookmarked = MutableStateFlow(false)
    val isChapterBookmarked = _isChapterBookmarked.asStateFlow()

    fun toggleChapterBookmark() {
        val chapter = currentChapter ?: return
        screenModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val newBookmark = !chapter.bookmark
            val updated = chapter.copy(bookmark = newBookmark)
            mangaRepository.updateChapterReadStatus(updated)
            currentChapter = updated
            _isChapterBookmarked.value = newBookmark
        }
    }

    fun navigateToChapter(chapter: Chapter) {
        loadChapter(mangaId, chapter.id)
    }

    fun setCustomTapZone(index: Int, action: ReaderPreferences.TapAction) {
        val current = _customTapZones.value.toMutableList()
        if (index in current.indices) {
            current[index] = action
            _customTapZones.value = current
            readerPreferences.customTapZones.set(current.joinToString(",") { it.ordinal.toString() })
        }
    }

    /**
     * Tải link ảnh thực tế cho trang online (Hỗ trợ getImageUrl 2 bước như Mihon)
     */
    fun loadPage(index: Int) {
        val pages = _allPages.value
        if (index !in pages.indices) return
        val page = pages[index] as? ReaderPage.Online ?: return

        // Bỏ qua nếu đã tải xong hoặc đang tải
        if (page.localFile != null || page.isLoading) return

        // Cancel job cũ nếu có (tránh duplicate request)
        pageLoadJobs[index]?.cancel()

        pageLoadJobs[index] = screenModelScope.launch {
            try {
                // Mark loading bằng copy() để trigger recompose
                val loadingPages = _allPages.value.toMutableList()
                if (index in loadingPages.indices) {
                    loadingPages[index] = page.copy(isLoading = true, hasError = false)
                    _allPages.value = loadingPages
                }

                val manga = mangaRepository.getMangaById(mangaId) ?: return@launch
                val source = sourceManager.get(manga.source) as? CatalogueSource ?: return@launch

                val sChapter = eu.kanade.tachiyomi.source.model.SChapter.create().apply {
                    // Fix: lấy URL từ page.chapter thay vì currentChapter để đúng khi continuous scroll
                    val chapter = page.chapter ?: currentChapter
                    url = chapter?.url ?: ""
                    name = chapter?.name ?: ""
                }

                // 1. Lấy URL ảnh thực tế (nếu chưa có)
                val imageUrl = if (page.url.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        val tPage = page.originalPage ?: eu.kanade.tachiyomi.source.model.Page(index, page.pageUrl, "")
                        (source as? HttpSource)?.getImageUrl(tPage) ?: ""
                    }
                } else page.url

                if (imageUrl.isEmpty()) {
                    // Cập nhật state: không còn loading
                    val updatedPages = _allPages.value.toMutableList()
                    if (index in updatedPages.indices) {
                        (updatedPages[index] as? ReaderPage.Online)?.let {
                            updatedPages[index] = it.copy(isLoading = false, hasError = true)
                        }
                        _allPages.value = updatedPages
                    }
                    return@launch
                }

                // 2. Download ảnh với headers của source
                // Fix: JsSource không phải HttpSource → phải check riêng để lấy đúng client và headers
                val httpSource = source as? HttpSource
                val jsSource = source as? com.example.manga_readerver2.source_js.JsSource
                val networkHelper = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
                val okHttpClient: okhttp3.OkHttpClient = httpSource?.client
                    ?: networkHelper.cloudflareClient
                val sourceHeaders: okhttp3.Headers = httpSource?.headers
                    ?: jsSource?.headers
                    ?: okhttp3.Headers.headersOf()
                var finalHeaders = sourceHeaders
                if (finalHeaders["Referer"] == null && sChapter.url.isNotBlank()) {
                    try {
                        val chapterUrlStr = if (sChapter.url.startsWith("http")) sChapter.url else "https://${sChapter.url}"
                        val uri = java.net.URI(chapterUrlStr)
                        if (uri.host != null) {
                            val referer = "${uri.scheme}://${uri.host}/"
                            finalHeaders = finalHeaders.newBuilder().add("Referer", referer).build()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }

                val request = okhttp3.Request.Builder()
                    .url(imageUrl)
                    .headers(finalHeaders)
                    .build()

                val onlineTempFile = File(context.cacheDir, "online_${chapterId}_${index}_${imageUrl.hashCode()}.jpg")
                withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val body = response.body ?: throw Exception("Empty body")
                        onlineTempFile.outputStream().use { fileOut ->
                            body.byteStream().use { bodyStream ->
                                bodyStream.copyTo(fileOut)
                            }
                        }
                    }
                }

                // Auto-detect Webtoon mode if the image is extremely tall (e.g. Manhwa long strip)
                // Only if the user hasn't explicitly set a custom reading mode for this manga (viewerFlags == 0)
                if (_readingMode.value != ReadingMode.WEBTOON && (manga?.viewerFlags ?: 0) == 0) {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(onlineTempFile.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        val ratio = options.outWidth.toFloat() / options.outHeight.toFloat()
                        // If aspect ratio is < 0.5 (height is more than 2x width), it's highly likely a Webtoon
                        if (ratio < 0.5f) {
                            _readingMode.value = ReadingMode.WEBTOON
                        }
                    }
                }

                // Fix: Dùng copy() thay vì mutate trực tiếp để StateFlow trigger recompose đúng
                val finalPages = _allPages.value.toMutableList()
                if (index in finalPages.indices) {
                    (finalPages[index] as? ReaderPage.Online)?.let {
                        finalPages[index] = it.copy(
                            url = imageUrl,
                            localFile = onlineTempFile.absolutePath,
                            isLoading = false,
                            hasError = false
                        )
                    }
                    _allPages.value = finalPages
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                val errorPages = _allPages.value.toMutableList()
                if (index in errorPages.indices) {
                    (errorPages[index] as? ReaderPage.Online)?.let {
                        errorPages[index] = it.copy(isLoading = false, hasError = true)
                    }
                    _allPages.value = errorPages
                }
                _errorMessage.value = "Lỗi tải trang $index: ${e.message}"
            } finally {
                pageLoadJobs.remove(index)
            }
        }
    }

    fun getSourceHeaders(): okhttp3.Headers? {
        val mangaSource = manga?.source ?: return null
        val source = sourceManager.get(mangaSource) ?: return null
        // Fix: JsSource không kế thừa HttpSource nên phải check cả hai
        var headers = when (source) {
            is eu.kanade.tachiyomi.source.online.HttpSource -> source.headers
            is com.example.manga_readerver2.source_js.JsSource -> source.headers
            else -> return null
        }
        
        // Add Referer if missing (crucial for LightNovel illustrations)
        if (headers["Referer"] == null) {
            val chapterUrl = currentChapter?.url
            if (!chapterUrl.isNullOrBlank()) {
                try {
                    val urlStr = if (chapterUrl.startsWith("http")) chapterUrl else "https://$chapterUrl"
                    val uri = java.net.URI(urlStr)
                    if (uri.host != null) {
                        val referer = "${uri.scheme}://${uri.host}/"
                        headers = headers.newBuilder().add("Referer", referer).build()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return headers
    }

    fun startTts() {
        val blocks = _textBlocks.value.filterIsInstance<ReaderBlock.Text>()
        if (blocks.isEmpty()) return
        val paragraphs = blocks.map { it.text }
        
        // Fix R4: Ưu tiên dùng pausedAtIndex từ TtsManager
        val startIndex = if (ttsManager.pausedAtIndex >= 0) 
            ttsManager.pausedAtIndex 
        else 
            currentTtsParagraph.value.coerceAtLeast(0)
            
        ttsManager.setSpeed(_ttsSpeed.value)
        ttsManager.setPitch(_ttsPitch.value)
        ttsManager.speak(paragraphs, startIndex)
    }

    fun ttsRewind() {
        val currentIndex = currentTtsParagraph.value
        if (currentIndex > 0) {
            ttsManager.stop()
            // Đặt lại index về phía trước 1 đoạn
            startTtsFromIndex(currentIndex - 1)
        }
    }

    fun ttsForward() {
        val currentIndex = currentTtsParagraph.value
        val blocks = _textBlocks.value.filterIsInstance<ReaderBlock.Text>()
        if (blocks.isEmpty()) return
        val paragraphs = blocks.map { it.text }
        
        if (currentIndex < paragraphs.size - 1) {
            ttsManager.stop()
            startTtsFromIndex(currentIndex + 1)
        }
    }

    private fun startTtsFromIndex(index: Int) {
        val blocks = _textBlocks.value.filterIsInstance<ReaderBlock.Text>()
        if (blocks.isEmpty()) return
        val paragraphs = blocks.map { it.text }
        ttsManager.speak(paragraphs, index)
    }

    fun pauseTts() {
        ttsManager.pause()
    }

    fun stopTts() {
        ttsManager.stop()
    }

    fun updateTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
        ttsManager.setSpeed(speed)
        readerPreferences.ttsSpeechRate.set(speed)
    }

    fun updateTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
        ttsManager.setPitch(pitch)
        readerPreferences.ttsPitch.set(pitch)
    }

    fun setTextPageCount(count: Int) {
        _pageCount.value = count
    }



    private fun downloadNextChapters() {
        val amount = readerPreferences.autoDownloadAmount.get()
        if (amount <= 0) return
        
        val manga = manga ?: return
        val currentChapter = currentChapter ?: return
        val source = sourceManager.get(manga.source) ?: return
        
        // Tìm các chương tiếp theo chưa đọc
        val nextChapters = currentMangaChapters
            .filter { it.chapterNumber > currentChapter.chapterNumber }
            .sortedBy { it.chapterNumber }
            .take(amount)
        
        if (nextChapters.isNotEmpty()) {
            downloadManager.downloadChapters(manga, nextChapters, source)
        }
    }

    override fun onDispose() {
        saveHistory()
        ttsManager.stop()
        zipFiles.values.forEach { it.close() }
        zipFiles.clear()
        pdfRenderers.values.forEach { it.close() }
        pdfRenderers.clear()
        pdfFileDescriptors.values.forEach { it.close() }
        pdfFileDescriptors.clear()
        super.onDispose()
        
        // Dọn dẹp cache trang PDF và ảnh online bằng GlobalScope để không bị hủy giữa chừng khi Screen đóng
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                // Xóa ảnh render PDF, ảnh online tạm, và các cache reader
                context.cacheDir.listFiles()?.filter { 
                    it.name.startsWith("pdf_") || it.name.startsWith("online_") || it.name.startsWith("reader_page_") || it.name.startsWith("cbz_")
                }?.forEach { 
                    it.delete()
                }
                
                val tempDir = File(context.cacheDir, "cbz_temp")
                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
