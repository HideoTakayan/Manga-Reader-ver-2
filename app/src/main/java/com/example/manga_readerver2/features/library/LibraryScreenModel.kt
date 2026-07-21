package com.example.manga_readerver2.features.library

import androidx.documentfile.provider.DocumentFile
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import com.example.manga_readerver2.core.source.ExtensionManager
import com.example.manga_readerver2.core.preference.LibraryPreferences
import com.example.manga_readerver2.core.utils.FileManager
import com.example.manga_readerver2.core.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File



class LibraryScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val fileManager: FileManager = Injekt.get()
) : ScreenModel {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMangaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMangaIds: StateFlow<Set<Long>> = _selectedMangaIds.asStateFlow()

    enum class SortMode {
        DATE_ADDED, TITLE, LAST_UPDATE
    }

    val sortMode: StateFlow<SortMode> = libraryPreferences.sortMode.asFlow()
        .map { SortMode.entries.getOrElse(it) { SortMode.DATE_ADDED } }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), SortMode.DATE_ADDED)

    val displayMode = libraryPreferences.displayMode.asFlow()
        .map { LibraryDisplayMode.entries.getOrElse(it) { LibraryDisplayMode.CompactGrid } }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), LibraryDisplayMode.CompactGrid)

    private val _selectedCategoryIndex = MutableStateFlow(0)
    val selectedCategoryIndex: StateFlow<Int> = _selectedCategoryIndex.asStateFlow()

    val categories = mangaRepository.getCategories()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filters = combine(
        libraryPreferences.filterDownloaded.asFlow(),
        libraryPreferences.filterUnread.asFlow(),
        libraryPreferences.filterStarted.asFlow(),
        libraryPreferences.filterBookmarked.asFlow()
    ) { fDownloaded, fUnread, fStarted, fBookmarked ->
        Triple(fDownloaded, fUnread, Pair(fStarted, fBookmarked))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val libraryItems: StateFlow<List<LibraryItem>> = combine(
        combine(_selectedCategoryIndex, categories, _searchQuery) { catIndex, cats, query -> 
            Triple(catIndex, cats, query)
        },
        _filters
    ) { baseInfo, filterInfo ->
        val (catIndex, cats, query) = baseInfo
        val (fDownloaded, fUnread, fPair) = filterInfo
        val (fStarted, fBookmarked) = fPair
        
        val selectedCat = cats.getOrNull(catIndex)
        // Fetch base list
        val baseFlow = if (query.isNotEmpty() || selectedCat == null) {
            mangaRepository.getLibrary()
        } else {
            mangaRepository.getMangasInCategory(selectedCat.id).map { list ->
                list.map { m -> com.example.manga_readerver2.domain.model.LibraryManga(m, 0) }
            }
        }
        
        baseFlow.map { list ->
            // Tối ưu hoá truy vấn cơ sở dữ liệu: Trích xuất thống kê toàn bộ thư viện bằng một truy vấn duy nhất
            // để thay thế cho việc gọi truy vấn riêng lẻ trên từng bộ truyện (giảm độ phức tạp từ O(n) xuống O(1))
            val statsMap = mangaRepository.getLibraryStats()

            list.map { item ->
                val manga = item.manga
                val sourceName = sourceManager.get(manga.source)?.name ?: "Local"
                val mangaDir = fileManager.getMangaPath(sourceName, manga.title, manga.id.toString())
                val hasDownloads = mangaDir.exists() && mangaDir.listFiles()?.any { it.extension == "cbz" || it.extension == "epub" } == true

                val stats = statsMap[manga.id]
                val hasStarted = stats?.first ?: false
                val hasBookmark = stats?.second ?: false

                LibraryItem(
                    manga = manga,
                    unreadCount = item.unreadCount,
                    isDownloaded = hasDownloads,
                    hasStarted = hasStarted,
                    hasBookmark = hasBookmark
                )
            }.filter { item ->
                // Apply Tri-state Filters
                val matchDownloaded = when (com.example.manga_readerver2.core.preference.TriState.entries[fDownloaded]) {
                    com.example.manga_readerver2.core.preference.TriState.DISABLED -> true
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_IS -> item.isDownloaded
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_NOT -> !item.isDownloaded
                }

                val matchUnread = when (com.example.manga_readerver2.core.preference.TriState.entries[fUnread]) {
                    com.example.manga_readerver2.core.preference.TriState.DISABLED -> true
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_IS -> item.unreadCount > 0
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_NOT -> item.unreadCount == 0
                }

                val matchStarted = when (com.example.manga_readerver2.core.preference.TriState.entries[fStarted]) {
                    com.example.manga_readerver2.core.preference.TriState.DISABLED -> true
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_IS -> item.hasStarted
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_NOT -> !item.hasStarted
                }

                val matchBookmarked = when (com.example.manga_readerver2.core.preference.TriState.entries[fBookmarked]) {
                    com.example.manga_readerver2.core.preference.TriState.DISABLED -> true
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_IS -> item.hasBookmark
                    com.example.manga_readerver2.core.preference.TriState.ENABLED_NOT -> !item.hasBookmark
                }

                matchDownloaded && matchUnread && matchStarted && matchBookmarked
            }.let { filteredList ->
                if (query.isEmpty()) filteredList else filteredList.filter { it.manga.title.contains(query, ignoreCase = true) }
            }
        }.flowOn(Dispatchers.IO)  // Thay đổi ngữ cảnh thực thi (Context) của Flow sang luồng IO để đảm bảo mượt mà
    }.flatMapLatest { it }
    .combine(sortMode) { list, sort ->
        when (sort) {
            SortMode.DATE_ADDED -> list.sortedByDescending { it.manga.dateAdded }
            SortMode.TITLE -> list.sortedBy { it.manga.title }
            SortMode.LAST_UPDATE -> list.sortedByDescending { it.manga.lastUpdate }
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedCategory(index: Int) {
        _selectedCategoryIndex.value = index
    }

    val history = mangaRepository.getHistory()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        libraryPreferences.sortMode.set(mode.ordinal)
    }

    fun unfollowManga(mangaId: Long) {
        screenModelScope.launch {
            mangaRepository.updateMangaFavorite(mangaId, false)
        }
    }

    fun toggleSelection(mangaId: Long) {
        val current = _selectedMangaIds.value
        if (current.contains(mangaId)) {
            _selectedMangaIds.value = current - mangaId
        } else {
            _selectedMangaIds.value = current + mangaId
        }
    }

    fun clearSelection() {
        _selectedMangaIds.value = emptySet()
    }

    fun bulkUnfollow(deleteDownloads: Boolean = false) {
        val ids = _selectedMangaIds.value
        screenModelScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                mangaRepository.updateMangaFavorite(id, false)
                
                if (deleteDownloads) {
                    val manga = mangaRepository.getMangaById(id)
                    if (manga != null) {
                        val sourceName = sourceManager.get(manga.source)?.name ?: manga.source.toString()
                        fileManager.deleteManga(sourceName, manga.title, manga.id.toString())
                        val downloadCache = Injekt.get<com.example.manga_readerver2.core.download.DownloadCache>()
                        downloadCache.removeManga(manga.id)
                    }
                }
            }
            clearSelection()
        }
    }

    fun bulkUpdate() {
        val ids = _selectedMangaIds.value
        screenModelScope.launch {
            _isLoading.value = true
            ids.forEach { id ->
                mangaRepository.getMangaById(id)?.let { manga ->
                    try {
                        updateMangaFromSource(manga)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _isLoading.value = false
            clearSelection()
        }
    }

    fun bulkMarkRead(read: Boolean) {
        val ids = _selectedMangaIds.value
        screenModelScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                val chapters = mangaRepository.getChaptersByMangaId(id)
                val updated = chapters.map { it.copy(read = read) }
                mangaRepository.insertChapters(updated)
            }
            clearSelection()
        }
    }

    fun bulkChangeCategory(categoryId: Long) {
        val ids = _selectedMangaIds.value
        screenModelScope.launch(Dispatchers.IO) {
            ids.forEach { mangaId ->
                mangaRepository.addMangaToCategory(mangaId, categoryId)
            }
            clearSelection()
        }
    }

    fun selectAll() {
        _selectedMangaIds.value = libraryItems.value.map { it.manga.id }.toSet()
    }

    fun invertSelection() {
        val allIds = libraryItems.value.map { it.manga.id }.toSet()
        val current = _selectedMangaIds.value
        _selectedMangaIds.value = allIds - current
    }

    fun clearAllHistory() {
        screenModelScope.launch {
            mangaRepository.deleteAllHistory()
        }
    }

    fun addCategory(name: String) {
        screenModelScope.launch {
            mangaRepository.insertCategory(name, 0)
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            mangaRepository.deleteCategory(categoryId)
        }
    }

    fun refreshLibrary() {
        screenModelScope.launch {
            _isLoading.value = true
            val items = mangaRepository.getLibrary().first()
            for (item in items) {
                try {
                    updateMangaFromSource(item.manga)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isLoading.value = false
        }
    }

    fun refreshCategory() {
        screenModelScope.launch {
            _isLoading.value = true
            val items = libraryItems.value
            for (item in items) {
                try {
                    updateMangaFromSource(item.manga)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isLoading.value = false
        }
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private suspend fun updateMangaFromSource(manga: Manga) {
        val source = sourceManager.get(manga.source) ?: return
        val sManga = eu.kanade.tachiyomi.source.model.SManga.create().apply {
            url = manga.url
            title = manga.title
            thumbnail_url = manga.thumbnailUrl ?: ""
        }
        
        try {
            // Không fetch lại chi tiết truyện (MangaDetails) để tăng tốc và tránh lỗi mạng,
            // chỉ tập trung lấy danh sách chương (ChapterList) như ý định của người dùng.

            val networkChapters = withContext(Dispatchers.IO) {
                source.getChapterList(sManga)
            }
            
            val existingChapters = mangaRepository.getChaptersByMangaId(manga.id)
            val existingUrls = existingChapters.map { it.url }.toSet()

            val newChapters = networkChapters.filter { it.url !in existingUrls }
            
            if (newChapters.isNotEmpty()) {
                val chapters = newChapters.mapIndexed { index, nc ->
                    com.example.manga_readerver2.domain.model.Chapter(
                        id = 0,
                        mangaId = manga.id,
                        url = nc.url,
                        name = nc.name,
                        scanlator = nc.scanlator,
                        read = false,
                        bookmark = false,
                        lastPageRead = 0,
                        dateFetch = System.currentTimeMillis(),
                        dateUpload = nc.date_upload,
                        chapterNumber = nc.chapter_number,
                        // Quan trọng: Gán sourceOrder để giữ đúng thứ tự khi MangaDetailScreen lấy danh sách.
                        // MangaDetailScreen dùng fallback sort theo sourceOrder/dateUpload khi chapterNumber = -1
                        sourceOrder = (networkChapters.size - index).toLong() 
                    )
                }
                mangaRepository.insertChapters(chapters)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Lỗi cập nhật '${manga.title}': ${e.message ?: "Không xác định"}"
        }
    }


    fun importManga(context: android.content.Context, uri: android.net.Uri) {
        screenModelScope.launch {
            _isLoading.value = true
            try {
                val contentResolver = context.contentResolver
                val fileName = getFileName(context, uri) ?: "imported_${System.currentTimeMillis()}"
                val extension = fileName.substringAfterLast(".", "").lowercase()
                
                var displayTitle = fileName.substringBeforeLast(".")
                var displayAuthor = "Local"
                var displayDesc = "Imported $extension file"
                var thumbUrl = ""

                val cacheFile = File(context.cacheDir, "import_temp_$fileName")
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                if (extension == "epub") {
                    val epubMeta = com.example.manga_readerver2.core.utils.EpubReader.getMetadata(cacheFile)
                    if (!epubMeta.title.isNullOrEmpty()) displayTitle = epubMeta.title
                    if (!epubMeta.author.isNullOrEmpty()) displayAuthor = epubMeta.author
                    if (!epubMeta.description.isNullOrEmpty()) displayDesc = epubMeta.description
                }

                val mangaId = mangaRepository.insertManga(
                    com.example.manga_readerver2.domain.model.Manga(
                        id = 0,
                        source = 0,
                        url = "local/$fileName",
                        title = displayTitle,
                        artist = "",
                        author = displayAuthor,
                        description = displayDesc,
                        genre = listOf(extension.uppercase(), "Local"),
                        status = if (extension == "epub" || extension == "pdf") 2L else 1L,
                        thumbnailUrl = "",
                        favorite = true,
                        initialized = true,
                        dateAdded = System.currentTimeMillis()
                    )
                )

                val mangaDir = fileManager.getMangaPath("Local", displayTitle, mangaId.toString())
                if (!mangaDir.exists()) mangaDir.mkdirs()
                val destFile = File(mangaDir, fileName)
                
                withContext(Dispatchers.IO) {
                    cacheFile.inputStream().use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    cacheFile.delete()
                }

                // Extract Cover
                if (extension == "epub") {
                    val epubMeta = com.example.manga_readerver2.core.utils.EpubReader.getMetadata(destFile)
                    if (!epubMeta.coverPath.isNullOrEmpty()) {
                        val coverBytes = com.example.manga_readerver2.core.utils.EpubReader.getCoverBytes(destFile, epubMeta.coverPath)
                        coverBytes?.let { bytes ->
                            val coverFile = File(mangaDir, "cover.jpg")
                            coverFile.writeBytes(bytes)
                            thumbUrl = coverFile.absolutePath
                        }
                    }
                } else if (extension == "cbz" || extension == "zip") {
                    val coverBytes = com.example.manga_readerver2.core.utils.ArchiveReader.getFirstImageBytes(destFile)
                    coverBytes?.let { bytes ->
                        val coverFile = File(mangaDir, "cover.jpg")
                        coverFile.writeBytes(bytes)
                        thumbUrl = coverFile.absolutePath
                    }
                } else if (extension == "pdf") {
                    try {
                        val pfd = android.os.ParcelFileDescriptor.open(destFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        if (renderer.pageCount > 0) {
                            val page = renderer.openPage(0)
                            // Sử dụng scale x2 để cover nét hơn
                            val bitmap = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            
                            val coverFile = File(mangaDir, "cover.jpg")
                            coverFile.outputStream().use { 
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
                            }
                            thumbUrl = coverFile.absolutePath
                        }
                        renderer.close()
                        pfd.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (thumbUrl.isNotEmpty()) {
                    mangaRepository.getMangaById(mangaId)?.let { m ->
                        mangaRepository.updateManga(m.copy(thumbnailUrl = thumbUrl))
                    }
                }

                // Create Chapters
                if (extension == "epub") {
                    val toc = com.example.manga_readerver2.core.utils.EpubReader.getToc(destFile)
                    if (toc.isNotEmpty()) {
                        val chapters = toc.mapIndexed { index, entry ->
                            val recognizedNumber = com.example.manga_readerver2.core.utils.ChapterRecognition.parseChapterNumber(entry.title)
                            com.example.manga_readerver2.domain.model.Chapter(
                                id = 0,
                                mangaId = mangaId,
                                url = "local/$fileName#${entry.href}",
                                name = entry.title,
                                dateUpload = System.currentTimeMillis(),
                                chapterNumber = if (recognizedNumber >= 0f) recognizedNumber else (index + 1).toFloat()
                            )
                        }
                        mangaRepository.insertChapters(chapters)
                    } else {
                        // Fallback: 1 chapter
                        mangaRepository.insertChapters(listOf(
                            com.example.manga_readerver2.domain.model.Chapter(
                                id = 0,
                                mangaId = mangaId,
                                url = "local/$fileName",
                                name = displayTitle,
                                dateUpload = System.currentTimeMillis(),
                                chapterNumber = 1f
                            )
                        ))
                    }
                } else {
                    // CBZ/ZIP or other: 1 chapter
                    mangaRepository.insertChapters(listOf(
                        com.example.manga_readerver2.domain.model.Chapter(
                            id = 0,
                            mangaId = mangaId,
                            url = "local/$fileName",
                            name = if (extension == "cbz") displayTitle else "Nội dung",
                            dateUpload = System.currentTimeMillis(),
                            chapterNumber = 1f
                        )
                    ))
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoading.value = false
        }
    }

    fun importFolder(context: android.content.Context, uri: android.net.Uri) {
        screenModelScope.launch {
            _isLoading.value = true
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                if (documentFile != null && documentFile.isDirectory) {
                    val folderName = documentFile.name ?: "imported_folder_${System.currentTimeMillis()}"
                    val children = documentFile.listFiles()
                    val contentResolver = context.contentResolver

                    val archiveExtensions = setOf("cbz", "zip", "epub", "pdf")
                    val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

                    val archiveFiles = children.filter { child ->
                        val ext = child.name?.substringAfterLast(".", "")?.lowercase()
                        val mime = child.type?.lowercase()
                        child.isFile && (ext in archiveExtensions || 
                            mime == "application/zip" || 
                            mime == "application/x-cbz" || 
                            mime == "application/epub+zip" || 
                            mime == "application/pdf" ||
                            mime == "application/octet-stream" && ext in archiveExtensions)
                    }
                    val imageFiles = children.filter { child ->
                        child.isFile && (child.name?.substringAfterLast(".", "")?.lowercase() in imageExtensions)
                    }

                    if (archiveFiles.isNotEmpty()) {
                        // Gom nhóm các file archive trong cùng một thư mục vào một bộ truyện duy nhất
                        val displayTitle = folderName
                        val displayAuthor = "Local"
                        val displayDesc = "Imported from folder: $folderName"
                        
                        // Kiểm tra xem manga đã tồn tại chưa bằng URL (local/folderName)
                        val existingManga = mangaRepository.getMangaByUrlAndSource("local/$folderName", 0L)
                        val mangaId = if (existingManga != null) {
                            existingManga.id
                        } else {
                            mangaRepository.insertManga(
                                com.example.manga_readerver2.domain.model.Manga(
                                    id = 0, source = 0,
                                    url = "local/$folderName",
                                    title = displayTitle, artist = "", author = displayAuthor,
                                    description = displayDesc,
                                    genre = listOf("Local", "Folder"),
                                    status = 1L,
                                    thumbnailUrl = "", favorite = true, initialized = true,
                                    dateAdded = System.currentTimeMillis()
                                )
                            )
                        }

                        val mangaDir = fileManager.getMangaPath("Local", displayTitle, mangaId.toString())
                        if (!mangaDir.exists()) mangaDir.mkdirs()

                        val chapters = mutableListOf<com.example.manga_readerver2.domain.model.Chapter>()
                        var firstThumbUrl = ""

                        archiveFiles.sortedBy { it.name }.forEachIndexed { index, archiveDoc ->
                            val childName = archiveDoc.name ?: return@forEachIndexed
                            val childExt = childName.substringAfterLast(".", "").lowercase()
                            val destFile = File(mangaDir, childName)

                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(archiveDoc.uri)?.use { input ->
                                    destFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }

                            // Extract cover from the first file in the folder
                            if (firstThumbUrl.isEmpty()) {
                                try {
                                    val thumbBytes = when (childExt) {
                                        "epub" -> {
                                            val meta = com.example.manga_readerver2.core.utils.EpubReader.getMetadata(destFile)
                                            if (!meta.coverPath.isNullOrEmpty()) {
                                                com.example.manga_readerver2.core.utils.EpubReader.getCoverBytes(destFile, meta.coverPath)
                                            } else null
                                        }
                                        "cbz", "zip" -> com.example.manga_readerver2.core.utils.ArchiveReader.getFirstImageBytes(destFile)
                                        "pdf" -> {
                                            val pfd = android.os.ParcelFileDescriptor.open(destFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                            val renderer = android.graphics.pdf.PdfRenderer(pfd)
                                            val bytes = if (renderer.pageCount > 0) {
                                                val page = renderer.openPage(0)
                                                val bmp = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                page.close()
                                                val stream = java.io.ByteArrayOutputStream()
                                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                                                val b = stream.toByteArray()
                                                bmp.recycle()
                                                b
                                            } else null
                                            renderer.close(); pfd.close()
                                            bytes
                                        }
                                        else -> null
                                    }

                                    thumbBytes?.let {
                                        val coverFile = File(mangaDir, "cover.jpg")
                                        coverFile.writeBytes(it)
                                        firstThumbUrl = coverFile.absolutePath
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }

                            // Add as chapter
                            val recognizedNumber = com.example.manga_readerver2.core.utils.ChapterRecognition.parseChapterNumber(childName.substringBeforeLast("."))
                            chapters.add(
                                com.example.manga_readerver2.domain.model.Chapter(
                                    id = 0, mangaId = mangaId,
                                    url = "local/$childName",
                                    name = childName.substringBeforeLast("."),
                                    dateUpload = System.currentTimeMillis(),
                                    chapterNumber = if (recognizedNumber >= 0f) recognizedNumber else (index + 1).toFloat()
                                )
                            )
                        }

                        if (firstThumbUrl.isNotEmpty()) {
                            mangaRepository.getMangaById(mangaId)?.let { m ->
                                mangaRepository.updateManga(m.copy(thumbnailUrl = firstThumbUrl))
                            }
                        }
                        mangaRepository.insertChapters(chapters)
                    } else if (imageFiles.isNotEmpty()) {
                        // Thư mục chỉ chứa ảnh → nén thành 1 CBZ
                        val tempDir = File(context.cacheDir, "temp_folder_$folderName")
                        if (!tempDir.exists()) tempDir.mkdirs()
                        for (child in imageFiles.sortedBy { it.name }) {
                            val childName = child.name ?: continue
                            val destFile = File(tempDir, childName)
                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(child.uri)?.use { i ->
                                    destFile.outputStream().use { o -> i.copyTo(o) }
                                }
                            }
                        }
                        val cbzFileName = "$folderName.cbz"
                        val cbzTempFile = File(context.cacheDir, "import_temp_$cbzFileName")
                        val zipSuccess = withContext(Dispatchers.IO) {
                            com.example.manga_readerver2.core.utils.ZipUtil.zipDirectory(tempDir, cbzTempFile, deleteSource = true)
                        }
                        if (zipSuccess) {
                            val mangaId = mangaRepository.insertManga(
                                com.example.manga_readerver2.domain.model.Manga(
                                    id = 0, source = 0, url = "local/$cbzFileName",
                                    title = folderName, artist = "", author = "Local",
                                    description = "Imported from folder", genre = listOf("CBZ", "Local"),
                                    status = 1L, thumbnailUrl = "", favorite = true, initialized = true,
                                    dateAdded = System.currentTimeMillis()
                                )
                            )
                            val mangaDir = fileManager.getMangaPath("Local", folderName, mangaId.toString())
                            if (!mangaDir.exists()) mangaDir.mkdirs()
                            val destFile = File(mangaDir, cbzFileName)
                            withContext(Dispatchers.IO) {
                                cbzTempFile.inputStream().use { i -> destFile.outputStream().use { o -> i.copyTo(o) } }
                                cbzTempFile.delete()
                            }
                            var thumbUrl = ""
                            com.example.manga_readerver2.core.utils.ArchiveReader.getFirstImageBytes(destFile)?.let {
                                val coverFile = File(mangaDir, "cover.jpg")
                                coverFile.writeBytes(it)
                                thumbUrl = coverFile.absolutePath
                            }
                            if (thumbUrl.isNotEmpty()) {
                                mangaRepository.getMangaById(mangaId)?.let { m ->
                                    mangaRepository.updateManga(m.copy(thumbnailUrl = thumbUrl))
                                }
                            }
                            mangaRepository.insertChapters(listOf(
                                com.example.manga_readerver2.domain.model.Chapter(
                                    id = 0, mangaId = mangaId, url = "local/$cbzFileName",
                                    name = folderName, dateUpload = System.currentTimeMillis(), chapterNumber = 1f
                                )
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            refreshLibrary()
            _isLoading.value = false
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    fun getRandomManga(): Long? {
        val items = libraryItems.value
        return items.randomOrNull()?.manga?.id
    }
}
