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
import com.example.manga_readerver2.core.source.HttpSource

sealed class ReaderPage {
    data class Local(val file: File) : ReaderPage()
    data class Archive(val file: File, val entryName: String) : ReaderPage()
    data class Online(var url: String, val index: Int, var isLoading: Boolean = false, var localFile: String? = null) : ReaderPage()
    data class Text(val content: String) : ReaderPage()
    data class Pdf(val file: File, val pageIndex: Int) : ReaderPage()
}

enum class ReadingMode {
    HORIZONTAL, VERTICAL
}

class ReaderScreenModel(
    private val context: Context = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val fileManager: FileManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val ttsManager: com.example.manga_readerver2.core.tts.TtsManager = Injekt.get(),
    private val downloadManager: com.example.manga_readerver2.core.download.DownloadManager = Injekt.get()
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

    private val _incognitoMode = MutableStateFlow(readerPreferences.incognitoMode.get())
    val incognitoMode = _incognitoMode.asStateFlow()

    private val _autoDownloadAmount = MutableStateFlow(readerPreferences.autoDownloadAmount.get())
    val autoDownloadAmount = _autoDownloadAmount.asStateFlow()

    private val _readingMode = MutableStateFlow(ReadingMode.entries.getOrElse(readerPreferences.readingMode.get()) { ReadingMode.VERTICAL })
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    private val _orientation = MutableStateFlow(0) // 0: Auto, 1: Portrait, 2: Landscape
    val orientation = _orientation.asStateFlow()

    private val _readerTheme = MutableStateFlow(readerPreferences.theme.get()) // 0: Dark, 1: White, 2: Sepia
    val readerTheme = _readerTheme.asStateFlow()

    private val _cropBorders = MutableStateFlow(false)
    val cropBorders = _cropBorders.asStateFlow()

    private val _brightness = MutableStateFlow(1.0f) // 0.0 to 1.0 (1.0 is full bright)
    val brightness = _brightness.asStateFlow()

    private val _fontSize = MutableStateFlow(readerPreferences.fontSize.get())
    val fontSize = _fontSize.asStateFlow()

    private val _lineSpacing = MutableStateFlow(readerPreferences.lineSpacing.get())
    val lineSpacing = _lineSpacing.asStateFlow()

    private val _allPages = MutableStateFlow<List<ReaderPage>>(emptyList())
    val allPages: StateFlow<List<ReaderPage>> = _allPages.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isTextReader = MutableStateFlow(false)
    val isTextReader: StateFlow<Boolean> = _isTextReader.asStateFlow()

    private var mangaId: Long = 0L
    private var chapterId: Long = 0L
    private var manga: Manga? = null
    var currentChapter: Chapter? = null
        private set
    private var currentMangaChapters: List<Chapter> = emptyList()
    private var startTime: Long = 0L
    private var currentZipFile: java.util.zip.ZipFile? = null
    private var currentPdfRenderer: android.graphics.pdf.PdfRenderer? = null
    private var currentPdfFileDescriptor: android.os.ParcelFileDescriptor? = null
    // Track page load jobs để cancel khi chuyển chapter tránh hiển thị ảnh sai
    private val pageLoadJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    private val zipMutex = kotlinx.coroutines.sync.Mutex()
    private var chapterLoadJob: kotlinx.coroutines.Job? = null

    init {
        // Collect reactive preferences to update UI state
        screenModelScope.launch {
            readerPreferences.readingMode.asFlow().collect { modeInt ->
                _readingMode.value = ReadingMode.entries.getOrElse(modeInt) { ReadingMode.VERTICAL }
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
            readerPreferences.incognitoMode.asFlow().collect { _incognitoMode.value = it }
        }
        screenModelScope.launch {
            readerPreferences.autoDownloadAmount.asFlow().collect { _autoDownloadAmount.value = it }
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

        // Đóng file cũ nếu có
        currentZipFile?.close()
        currentZipFile = null
        currentPdfRenderer?.close()
        currentPdfRenderer = null
        currentPdfFileDescriptor?.close()
        currentPdfFileDescriptor = null

        screenModelScope.launch {
            try {
                manga = mangaRepository.getMangaById(mId)
                val allChapters = mangaRepository.getChaptersByMangaId(mId)
                _chapters.value = allChapters
                currentMangaChapters = allChapters
                currentChapter = allChapters.find { it.id == cId }
                
                val manga = manga ?: throw Exception("Không tìm thấy truyện")
                val currentChapter = currentChapter ?: throw Exception("Không tìm thấy chương")
                
                _chapterName.value = currentChapter.name
                
                val sourceName = extensionManager.getSource(manga.source)?.name ?: "Unknown"

                // 0. Kiểm tra xem chương này có đang trong quá trình tải xuống không
                val isDownloading = downloadManager.queueState.value.any { 
                    it.manga.id == mId && it.chapter.id == cId && it.status == com.example.manga_readerver2.core.download.Download.State.DOWNLOADING 
                }
                
                // Nếu đang tải, chúng ta đọc Online để tránh xung đột file đang bị ghi
                if (!isDownloading) {
                    // Thiết lập trang bắt đầu (Resume)
                    val lastPage = currentChapter.lastPageRead.toInt()
                    
                    // 0.1 Kiểm tra file cục bộ (Imported)
                if (currentChapter.url.startsWith("local/")) {
                    val localFileName = currentChapter.url.substringAfter("local/").substringBefore("#")
                    val sourceNameForPath = if (manga.source == 0L) "Local" else sourceName
                    val localFile = File(fileManager.getMangaPath(sourceNameForPath, manga.title, manga.id.toString()), localFileName)
                    
                    if (localFile.exists()) {
                        val extension = localFile.extension.lowercase()
                        if (extension == "cbz") {
                            currentZipFile = withContext(Dispatchers.IO) { java.util.zip.ZipFile(localFile) }
                            val entries = com.example.manga_readerver2.core.utils.ArchiveReader.getOrderedImageEntries(localFile)
                            val pages = entries.map { ReaderPage.Archive(localFile, it) }
                            _allPages.value = pages
                            _pageCount.value = pages.size
                            if (lastPage in 0 until pages.size) {
                                _currentPage.value = lastPage
                            }
                            _isLoading.value = false
                            saveHistory()
                            return@launch
                        } else if (extension == "pdf") {
                            val pfd = android.os.ParcelFileDescriptor.open(localFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = android.graphics.pdf.PdfRenderer(pfd)
                            currentPdfFileDescriptor = pfd
                            currentPdfRenderer = renderer
                            
                            val pages = (0 until renderer.pageCount).map { ReaderPage.Pdf(localFile, it) }
                            _allPages.value = pages
                            _pageCount.value = pages.size
                            if (lastPage in 0 until pages.size) {
                                _currentPage.value = lastPage
                            }
                            _isLoading.value = false
                            saveHistory()
                            return@launch
                        } else if (extension == "epub") {
                            _isTextReader.value = true
                            val href = currentChapter.url.substringAfter("#", "")
                            val content = withContext(Dispatchers.IO) { 
                                if (href.isNotEmpty()) {
                                    com.example.manga_readerver2.core.utils.EpubReader.getChapterText(localFile, href)
                                } else {
                                    com.example.manga_readerver2.core.utils.EpubReader.getFullText(localFile) 
                                }
                            }
                            _allPages.value = listOf(ReaderPage.Text(content))
                            _pageCount.value = 1
                            _isLoading.value = false
                            saveHistory()
                            return@launch
                        }
                    }
                }

                // 1. Kiểm tra file nén CBZ (Truyện tranh - Mihon style)
                // Fix O1: Fallback tìm file offline khi extension đã bị gỡ cài đặt
                var cbzFile = fileManager.getChapterCbzPath(sourceName, manga.title, manga.id.toString(), currentChapter.name)
                if (!cbzFile.exists()) {
                    // Cố gắng tìm bằng cách duyệt qua tất cả thư mục download nếu sourceName bị Unknown
                    val allDownloadsDir = fileManager.getDownloadPath()
                    val potentialDirs = allDownloadsDir.listFiles { dir -> dir.isDirectory }?.flatMap { it.listFiles()?.toList() ?: emptyList() }
                    val targetDir = potentialDirs?.find { it.name.contains(manga.id.toString().takeLast(6)) }
                    if (targetDir != null) {
                        cbzFile = File(targetDir, "${currentChapter.name}.cbz".replace(Regex("[\\\\/:*?\"<>|]"), "_").trim())
                    }
                }

                if (cbzFile.exists()) {
                    currentZipFile = withContext(Dispatchers.IO) { java.util.zip.ZipFile(cbzFile) }
                    val entries = ArchiveReader.getOrderedImageEntries(cbzFile)
                    val pages = entries.map { ReaderPage.Archive(cbzFile, it) }
                    _allPages.value = pages
                    _pageCount.value = pages.size
                    
                    if (lastPage in 0 until pages.size) {
                        _currentPage.value = lastPage
                    }
                    
                    _isLoading.value = false
                    saveHistory()
                    return@launch
                }

                // Kiểm tra file EPUB (Truyện chữ Offline đã tải từ Extension)
                var novelFile = fileManager.getChapterNovelPath(sourceName, manga.title, manga.id.toString(), currentChapter.name)
                if (!novelFile.exists()) {
                     val allDownloadsDir = fileManager.getDownloadPath()
                     val potentialDirs = allDownloadsDir.listFiles { dir -> dir.isDirectory }?.flatMap { it.listFiles()?.toList() ?: emptyList() }
                     val targetDir = potentialDirs?.find { it.name.contains(manga.id.toString().takeLast(6)) }
                     if (targetDir != null) {
                         novelFile = File(targetDir, "${currentChapter.name}.epub".replace(Regex("[\\\\/:*?\"<>|]"), "_").trim())
                     }
                }

                if (novelFile.exists()) {
                    _isTextReader.value = true
                    // Fix R3: Hiển thị đầy đủ HTML từ epub, dùng getFullText (vì epub này có thể từ external), 
                    // ToC có thể cần thiết cho màn hình lớn hơn nhưng tạm thời getFullText là fallback hiệu quả nhất
                    val content = withContext(Dispatchers.IO) { 
                        com.example.manga_readerver2.core.utils.EpubReader.getFullText(novelFile) 
                    }
                    _allPages.value = listOf(ReaderPage.Text(content))
                    _pageCount.value = 1
                    _isLoading.value = false
                    saveHistory()
                    return@launch
                }

                // 2. Nếu không tìm thấy file Offline hoàn chỉnh hoặc đang tải -> Đọc ONLINE từ nguồn
                val source = extensionManager.getSource(manga.source) as? CatalogueSource
                if (source != null) {
                    // SChapter mapping
                    val sChapter = eu.kanade.tachiyomi.source.model.SChapter.create().apply {
                        url = currentChapter.url
                        name = currentChapter.name
                    }
                        val networkPages = withContext(Dispatchers.IO) {
                            source.getPageList(sChapter)
                        }
                        
                        // Detect if it's text-based (JS VBook)
                        val extensions = extensionManager.installedExtensions.value
                        val sourceExt = extensions.find { ext -> ext.sources.any { it.id == manga.source } }
                        val isVBook = sourceExt?.isVBook == true

                        if (isVBook && networkPages.isNotEmpty()) {
                            val firstPage = networkPages.first()
                            val content = firstPage.imageUrl ?: ""
                            // Fix: detect text/HTML content chính xác hơn, không chỉ dựa vào length
                            val isHtmlContent = content.trimStart().let {
                                it.startsWith("<") || it.contains("<p") || it.contains("<div") || it.contains("<br")
                            }
                            // Fix: content.length > 50 có thể khớp URL CDN dài → raise lên 200
                            // Thêm check scheme rõ ràng: http/https/data:image là URL, không phải text
                            val looksLikeUrl = content.startsWith("http://") || content.startsWith("https://") || content.startsWith("data:")
                            val isTextContent = !looksLikeUrl && (isHtmlContent || content.length > 200)
                            if (isTextContent) {
                                _isTextReader.value = true
                                _allPages.value = listOf(ReaderPage.Text(content))
                                _pageCount.value = 1
                            } else {
                                val pages = networkPages.mapIndexed { index, page -> 
                                    ReaderPage.Online(page.imageUrl ?: "", index) 
                                }
                                _allPages.value = pages
                                _pageCount.value = pages.size
                                // Fix R1: Cần phải gọi loadPage cho truyện tranh VBook để tải ảnh thực tế
                                loadPage(lastPage.coerceIn(0, pages.size - 1))
                            }
                        } else {
                            val pages = networkPages.mapIndexed { index, page -> 
                                ReaderPage.Online(page.imageUrl ?: "", index) 
                            }
                            _allPages.value = pages
                            _pageCount.value = pages.size
                            loadPage(lastPage.coerceIn(0, pages.size - 1))
                        }
                        
                        if (lastPage in 0 until _pageCount.value) {
                            _currentPage.value = lastPage
                        }
                    } else {
                        _errorMessage.value = "Không tìm thấy nguồn hoặc chương chưa được tải."
                    }
                } else {
                    _errorMessage.value = "Chương đang được tải xuống. Vui lòng chờ."
                }

                _isLoading.value = false
                saveHistory()
                downloadNextChapters()
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Fix R2: Thêm suspend parameter để sử dụng được với mutex (vì zipMutex là coroutine mutex)
    // Hoặc sửa thành synchronized lock nếu không muốn suspend.
    // Vì ArchiveReader.getPageStream không suspend, ta dùng synchronized object cho đơn giản và hiệu quả.
    private val zipLock = Any()
    private val pdfLock = Any()
    
    fun getPageBytes(page: ReaderPage.Archive): ByteArray? {
        return try {
            synchronized(zipLock) {
                val zip = currentZipFile ?: return@synchronized null
                ArchiveReader.getPageStream(zip, page.entryName)?.readBytes()
            }
        } catch (e: java.util.zip.ZipException) {
            // Zip đã bị đóng (do chuyển chapter) → trả null, UI hiện loading rồi tự retry
            null
        } catch (e: Exception) {
            null
        }
    }

    fun renderPdfPage(page: ReaderPage.Pdf): String? {
        return try {
            // PdfRenderer không thread-safe → dùng synchronized lock
            synchronized(pdfLock) {
                val renderer = currentPdfRenderer ?: return@synchronized null
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun setPage(index: Int) {
        if (index in 0 until _pageCount.value) {
            _currentPage.value = index
            saveHistory()
            // Tải trước nếu ở gần cuối chương (giống Mihon)
            if (index > _pageCount.value * 0.8) {
                downloadNextChapters()
            }
        }
    }

    private var lastSavedPageIndex = -1
    private var lastSavedTime = 0L

    private fun saveHistory() {
        if (_incognitoMode.value) return
        
        val index = _currentPage.value
        val cId = chapterId
        val chapter = currentChapter
        val totalPages = _pageCount.value
        
        // Tránh lưu liên tục nếu dữ liệu không đổi đáng kể
        val now = System.currentTimeMillis()
        val timeDiff = now - lastSavedTime
        if (index == lastSavedPageIndex && timeDiff < 5000) return 

        screenModelScope.launch(Dispatchers.IO) {
            // Cập nhật lịch sử đọc
            val timeRead = if (startTime > 0) now - startTime else 0L
            if (timeRead > 0 || index != lastSavedPageIndex) {
                mangaRepository.upsertHistory(cId, now, timeRead)
                startTime = now
                lastSavedTime = now
                lastSavedPageIndex = index

                // Cập nhật trang cuối cùng trong bản ghi chapter
                if (index >= 0 && chapter != null) {
                    val isRead = totalPages > 0 && index >= totalPages - 1
                    mangaRepository.updateChapter(chapter.copy(
                        lastPageRead = index.toLong(),
                        read = if (isRead) true else chapter.read
                    ))
                }
            }
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        readerPreferences.readingMode.set(mode.ordinal)
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
        readerPreferences.incognitoMode.set(enabled)
    }

    fun setAutoDownloadAmount(amount: Int) {
        _autoDownloadAmount.value = amount
        readerPreferences.autoDownloadAmount.set(amount)
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        readerPreferences.fontSize.set(size)
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

    fun setCropBorders(enabled: Boolean) {
        _cropBorders.value = enabled
    }

    fun setBrightness(value: Float) {
        _brightness.value = value
    }

    fun navigateToChapter(chapter: Chapter) {
        loadChapter(mangaId, chapter.id)
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
                    loadingPages[index] = page.copy(isLoading = true)
                    _allPages.value = loadingPages
                }

                val manga = mangaRepository.getMangaById(mangaId) ?: return@launch
                val source = extensionManager.getSource(manga.source) as? CatalogueSource ?: return@launch

                val sChapter = eu.kanade.tachiyomi.source.model.SChapter.create().apply {
                    val chapter = currentChapter
                    url = chapter?.url ?: ""
                    name = chapter?.name ?: ""
                }

                // 1. Lấy URL ảnh thực tế (nếu chưa có)
                val imageUrl = if (page.url.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        val tPage = eu.kanade.tachiyomi.source.model.Page(index, sChapter.url, "")
                        (source as? HttpSource)?.getImageUrl(tPage) ?: ""
                    }
                } else page.url

                if (imageUrl.isEmpty()) {
                    // Cập nhật state: không còn loading
                    val updatedPages = _allPages.value.toMutableList()
                    if (index in updatedPages.indices) {
                        (updatedPages[index] as? ReaderPage.Online)?.let {
                            updatedPages[index] = it.copy(isLoading = false)
                        }
                        _allPages.value = updatedPages
                    }
                    return@launch
                }

                // 2. Download ảnh với headers của source để tránh 403 Forbidden
                val okHttpClient: okhttp3.OkHttpClient = uy.kohesive.injekt.Injekt.get()
                val sourceHeaders = (source as? HttpSource)?.headers ?: okhttp3.Headers.headersOf()
                val request = okhttp3.Request.Builder()
                    .url(imageUrl)
                    .headers(sourceHeaders)
                    .build()

                val bytes = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        response.body?.bytes() ?: throw Exception("Empty body")
                    }
                }

                val onlineTempFile = File(context.cacheDir, "online_${index}_${imageUrl.hashCode()}.jpg")
                withContext(Dispatchers.IO) { onlineTempFile.writeBytes(bytes) }

                // Fix: Dùng copy() thay vì mutate trực tiếp để StateFlow trigger recompose đúng
                val finalPages = _allPages.value.toMutableList()
                if (index in finalPages.indices) {
                    (finalPages[index] as? ReaderPage.Online)?.let {
                        finalPages[index] = it.copy(
                            url = imageUrl,
                            localFile = onlineTempFile.absolutePath,
                            isLoading = false
                        )
                    }
                    _allPages.value = finalPages
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                val errorPages = _allPages.value.toMutableList()
                if (index in errorPages.indices) {
                    (errorPages[index] as? ReaderPage.Online)?.let {
                        errorPages[index] = it.copy(isLoading = false)
                    }
                    _allPages.value = errorPages
                }
                _errorMessage.value = "Lỗi tải trang $index: ${e.message}"
            } finally {
                pageLoadJobs.remove(index)
            }
        }
    }

    fun startTts() {
        val pages = _allPages.value
        val textPage = pages.firstOrNull() as? ReaderPage.Text ?: return
        val paragraphs = textPage.content.split("\n").filter { it.isNotBlank() }
        
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
        val pages = _allPages.value
        val textPage = pages.firstOrNull() as? ReaderPage.Text ?: return
        val paragraphs = textPage.content.split("\n").filter { it.isNotBlank() }
        
        if (currentIndex < paragraphs.size - 1) {
            ttsManager.stop()
            startTtsFromIndex(currentIndex + 1)
        }
    }

    private fun startTtsFromIndex(index: Int) {
        val pages = _allPages.value
        val textPage = pages.firstOrNull() as? ReaderPage.Text ?: return
        val paragraphs = textPage.content.split("\n").filter { it.isNotBlank() }
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
        val source = extensionManager.getSource(manga.source) ?: return
        
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
        // Fix BUG-15: Gọi release() để shutdown TTS engine và giải phóng TTS service connection
        ttsManager.release()
        currentZipFile?.close()
        currentZipFile = null
        currentPdfRenderer?.close()
        currentPdfRenderer = null
        currentPdfFileDescriptor?.close()
        currentPdfFileDescriptor = null
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
