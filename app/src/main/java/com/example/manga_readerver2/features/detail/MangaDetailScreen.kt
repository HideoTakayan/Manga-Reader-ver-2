@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.example.manga_readerver2.features.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.example.manga_readerver2.core.navigation.LocalNavAnimatedVisibilityScope
import com.example.manga_readerver2.core.navigation.LocalSharedTransitionScope
import com.example.manga_readerver2.core.utils.HtmlParser
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.model.Category
import com.example.manga_readerver2.features.downloads.DownloadQueueScreen
import com.example.manga_readerver2.features.reader.ReaderScreen
import com.example.manga_readerver2.ui.theme.*
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.coroutines.launch

data class MangaDetailScreen(val mangaId: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { MangaDetailScreenModel() }
        val mangaState by screenModel.manga.collectAsState()
        val chapters by screenModel.chapters.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val isLiked by screenModel.isLiked.collectAsState()
        val sortMode by screenModel.sortMode.collectAsState()
        val filterMode by screenModel.filterMode.collectAsState()
        val source by screenModel.source.collectAsState()
        val errorMessage by screenModel.errorMessage.collectAsState()

        val selectedChapterIds by screenModel.selectedChapterIds.collectAsState()
        val isSelectionMode by screenModel.isSelectionMode.collectAsState()

        val listState = rememberLazyListState()
        val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }
        
        var showChapterDialog by remember { mutableStateOf(false) }
        var showCategoryDialog by remember { mutableStateOf(false) }
        var showCoverDialog by remember { mutableStateOf(false) }
        var selectedCategoryIds by remember { mutableStateOf(emptyList<Long>()) }

        val manga = mangaState

        LaunchedEffect(showCategoryDialog) {
            if (showCategoryDialog) {
                selectedCategoryIds = screenModel.getMangaCategoryIds()
            }
        }

        LaunchedEffect(mangaId) {
            screenModel.loadMangaDetail(mangaId)
        }

        val isFabExpanded = remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                MangaDetailTopBar(
                    title = if (isScrolled) manga?.title ?: "" else "",
                    onBack = { navigator.pop() },
                    onDownloadQueue = { navigator.push(DownloadQueueScreen()) },
                    onRefresh = { screenModel.refreshManual() },
                    isHttpSource = source is eu.kanade.tachiyomi.source.online.HttpSource,
                    isScrolled = isScrolled,
                    onDownloadAction = { screenModel.runDownloadAction(it) }
                )
            },
            bottomBar = {
                if (isSelectionMode) {
                    MangaDetailBottomActionBar(
                        selectedCount = selectedChapterIds.size,
                        onDownload = {
                            screenModel.downloadChapters(selectedChapterIds.toList())
                            screenModel.clearSelection()
                        },
                        onMarkRead = {
                            screenModel.markChaptersRead(selectedChapterIds.toList(), true)
                            screenModel.clearSelection()
                        },
                        onMarkUnread = {
                            screenModel.markChaptersRead(selectedChapterIds.toList(), false)
                            screenModel.clearSelection()
                        },
                        onDelete = {
                            Toast.makeText(context, "Đã xóa ${selectedChapterIds.size} chương (đang hoàn thiện)", Toast.LENGTH_SHORT).show()
                            screenModel.clearSelection()
                        }
                    )
                }
            },
            floatingActionButton = {
                if (manga != null && chapters.isNotEmpty() && !isSelectionMode) {
                    val nextToRead = chapters.findLast { !it.read } ?: chapters.firstOrNull()
                    if (nextToRead != null) {
                        ExtendedFloatingActionButton(
                            onClick = { navigator.push(ReaderScreen(manga.id, nextToRead.id)) },
                            containerColor = PrimaryOrange,
                            contentColor = Color.White,
                            expanded = isFabExpanded.value,
                            icon = { Icon(Icons.Default.PlayArrow, null) },
                            text = { Text(if (nextToRead.read) "Bắt đầu lại" else "Đọc tiếp", fontWeight = FontWeight.Bold) },
                            shape = CircleShape
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading && manga == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryOrange)
                        }
                    }
                    errorMessage != null && manga == null -> {
                        // Hiện lỗi rõ ràng thay vì blank screen
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = errorMessage ?: "Lỗi không xác định",
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { navigator.pop() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                                ) { Text("Quay lại") }
                            }
                        }
                    }
                    manga != null -> {
                    MangaDetailContent(
                        paddingValues = paddingValues,
                        listState = listState,
                        manga = manga,
                        sourceName = source?.name ?: "Unknown",
                        chapters = chapters,
                        downloadStatus = screenModel.downloadStatus.collectAsState().value,
                        isLiked = isLiked,
                        onFavoriteClick = {
                            if (isLiked) {
                                // Đang liked → mở dialog để đổi category hoặc unfavorite
                                showCategoryDialog = true
                            } else {
                                // Chưa liked → toggle favorite rồi mở dialog chọn category (chuẩn Mihon)
                                screenModel.toggleLike()
                                showCategoryDialog = true
                            }
                        },
                        onChapterClick = { chapter ->
                            navigator.push(ReaderScreen(manga.id, chapter.id))
                        },
                        onDownloadChapter = { screenModel.downloadChapter(it) },
                        onFilterClick = { showChapterDialog = true },
                        onCoverClick = { showCoverDialog = true },
                        onWebViewClick = {
                            val httpSource = source as? HttpSource
                            val mangaUrl = if (httpSource != null) "${httpSource.baseUrl}${manga.url}" else null
                            if (mangaUrl != null) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mangaUrl))
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Nguồn không hỗ trợ xem trên trình duyệt", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShareClick = {
                            val httpSource = source as? HttpSource
                            val mangaUrl = if (httpSource != null) "${httpSource.baseUrl}${manga.url}" else null
                            if (mangaUrl != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${manga.title}\n$mangaUrl")
                                }
                                context.startActivity(Intent.createChooser(intent, "Chia sẻ truyện"))
                            }
                        },
                        onTrackClick = {
                            Toast.makeText(context, "Chức năng theo dõi sẽ sớm ra mắt!", Toast.LENGTH_SHORT).show()
                        },
                        onTagClick = { tag ->
                            Toast.makeText(context, "Tìm kiếm tag: $tag", Toast.LENGTH_SHORT).show()
                        },
                        selectedChapterIds = selectedChapterIds,
                        isSelectionMode = isSelectionMode,
                        onToggleSelection = { id ->
                            screenModel.toggleSelection(id)
                        }
                    )
                    }
                }

                if (showChapterDialog) {
                    ChapterSettingsDialog(
                        currentSort = sortMode,
                        currentFilter = filterMode,
                        onDismiss = { showChapterDialog = false },
                        onSortChange = { screenModel.setSortMode(it) },
                        onFilterChange = { screenModel.setFilterMode(it) }
                    )
                }
                
                if (showCategoryDialog) {
                    val allCategories by screenModel.categories.collectAsState()
                    CategorySelectionDialog(
                        allCategories = allCategories,
                        selectedIds = selectedCategoryIds,
                        onDismiss = { showCategoryDialog = false },
                        onConfirm = { ids ->
                            screenModel.updateMangaCategories(ids)
                            showCategoryDialog = false
                        },
                        onUnfavorite = {
                            screenModel.toggleLike()
                            showCategoryDialog = false
                        }
                    )
                }

                if (showCoverDialog && manga != null) {
                    MangaCoverDialog(
                        manga = manga,
                        onDismiss = { showCoverDialog = false }
                    )
                }
            }
        }
    }
    
    private fun Manga.toSManga() = eu.kanade.tachiyomi.source.model.SManga.create().apply {
        url = this@toSManga.url
        title = this@toSManga.title
        thumbnail_url = this@toSManga.thumbnailUrl
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = this@toSManga.description
        genre = this@toSManga.genre?.joinToString(", ")
        status = this@toSManga.status.toInt()
    }
}

@Composable
fun MangaDetailTopBar(
    title: String,
    onBack: () -> Unit,
    onDownloadQueue: () -> Unit,
    onRefresh: () -> Unit,
    isHttpSource: Boolean,
    isScrolled: Boolean,
    onDownloadAction: (DownloadAction) -> Unit
) {
    val backgroundColor by androidx.compose.animation.animateColorAsState(if (isScrolled) Color(0xFF1E1E1E) else Color.Transparent, label = "bg")
    var showDownloadMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 18.sp
            )
            
            Box {
                IconButton(onClick = { showDownloadMenu = true }) {
                    Icon(Icons.Default.Download, "Download Options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showDownloadMenu,
                    onDismissRequest = { showDownloadMenu = false },
                    containerColor = Color(0xFF2B2B2B)
                ) {
                    DropdownMenuItem(
                        text = { Text("Tiếp theo 1 chương", color = Color.White) },
                        onClick = { showDownloadMenu = false; onDownloadAction(DownloadAction.NEXT_1_CHAPTER) }
                    )
                    DropdownMenuItem(
                        text = { Text("Tiếp theo 5 chương", color = Color.White) },
                        onClick = { showDownloadMenu = false; onDownloadAction(DownloadAction.NEXT_5_CHAPTERS) }
                    )
                    DropdownMenuItem(
                        text = { Text("Tiếp theo 10 chương", color = Color.White) },
                        onClick = { showDownloadMenu = false; onDownloadAction(DownloadAction.NEXT_10_CHAPTERS) }
                    )
                    DropdownMenuItem(
                        text = { Text("Chưa đọc", color = Color.White) },
                        onClick = { showDownloadMenu = false; onDownloadAction(DownloadAction.UNREAD_CHAPTERS) }
                    )
                    DropdownMenuItem(
                        text = { Text("Tất cả", color = Color.White) },
                        onClick = { showDownloadMenu = false; onDownloadAction(DownloadAction.ALL_CHAPTERS) }
                    )
                }
            }

            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Thêm", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    containerColor = Color(0xFF2B2B2B)
                ) {
                    DropdownMenuItem(
                        text = { Text("Hàng đợi tải xuống", color = Color.White) },
                        onClick = { showMoreMenu = false; onDownloadQueue() }
                    )
                    if (isHttpSource) {
                        DropdownMenuItem(
                            text = { Text("Làm mới", color = Color.White) },
                            onClick = { showMoreMenu = false; onRefresh() }
                        )
                        DropdownMenuItem(
                            text = { Text("Chia sẻ", color = Color.White) },
                            onClick = { showMoreMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun MangaDetailContent(
    paddingValues: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    manga: Manga,
    sourceName: String,
    chapters: List<Chapter>,
    isLiked: Boolean,
    onFavoriteClick: () -> Unit,
    onChapterClick: (Chapter) -> Unit,
    onDownloadChapter: (Chapter) -> Unit,
    onFilterClick: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClick: () -> Unit,
    onShareClick: () -> Unit,
    onTrackClick: () -> Unit,
    onTagClick: (String) -> Unit,
    downloadStatus: Map<Long, com.example.manga_readerver2.core.download.Download.State>,
    selectedChapterIds: Set<Long>,
    isSelectionMode: Boolean,
    onToggleSelection: (Long) -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                AsyncImage(
                    model = manga,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(4.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.2f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BackgroundDark
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .height(175.dp)
                            .clickable { onCoverClick() },
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                AsyncImage(
                                    model = manga,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedElement(
                                            rememberSharedContentState(key = "manga_cover_${manga.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                )
                            }
                        } else {
                            AsyncImage(
                                model = manga,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp, bottom = 4.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = manga.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = manga.author ?: "Unknown Author",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val (statusIcon, statusLabel, statusColor) = when (manga.status) {
                            1L -> Triple(Icons.Outlined.Schedule, "Đang tiến hành", Color.White.copy(alpha = 0.8f))
                            2L -> Triple(Icons.Outlined.DoneAll, "Đã hoàn thành", Color.White.copy(alpha = 0.8f))
                            else -> Triple(Icons.Outlined.Block, "Không rõ", Color.White.copy(alpha = 0.8f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(" • ", color = statusColor, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = sourceName,
                                color = statusColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val defaultColor = Color.White.copy(alpha = 0.6f)
                MangaActionButton(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    label = if (isLiked) "Trong thư viện" else "Thêm vào",
                    color = if (isLiked) PrimaryOrange else defaultColor,
                    onClick = onFavoriteClick,
                    modifier = Modifier.weight(1f)
                )
                MangaActionButton(
                    icon = Icons.Outlined.HourglassEmpty,
                    label = "Cập nhật",
                    color = defaultColor,
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )
                MangaActionButton(
                    icon = Icons.Outlined.Sync,
                    label = "Theo dõi",
                    color = defaultColor,
                    onClick = onTrackClick,
                    modifier = Modifier.weight(1f)
                )
                MangaActionButton(
                    icon = Icons.Outlined.Public,
                    label = "WebView",
                    color = defaultColor,
                    onClick = onWebViewClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            var isDescriptionExpanded by remember { mutableStateOf(false) }
            val descriptionText = remember(manga.description) {
                HtmlParser.extractCleanText(manga.description ?: "No description available.")
            }
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Box(modifier = Modifier.fillMaxWidth().animateContentSize().clickable { isDescriptionExpanded = !isDescriptionExpanded }) {
                    Text(
                        text = descriptionText,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    
                    if (!isDescriptionExpanded && descriptionText.length > 100) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, BackgroundDark)
                                    )
                                )
                        )
                    }
                }
                
                Box(
                    modifier = Modifier.fillMaxWidth().clickable { isDescriptionExpanded = !isDescriptionExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Description",
                        tint = Color.White
                    )
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                manga.genre?.forEach { genre ->
                    SuggestionChip(
                        onClick = { onTagClick(genre) },
                        label = { Text(text = genre, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${chapters.size} chương",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                IconButton(onClick = onFilterClick) {
                    Icon(Icons.Default.Sort, null, tint = Color.White)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.1f)
            )
        }

        items(chapters) { chapter ->
            val state = downloadStatus[chapter.id] ?: com.example.manga_readerver2.core.download.Download.State.NOT_DOWNLOADED
            val isSelected = selectedChapterIds.contains(chapter.id)
            ChapterItem(
                chapter = chapter,
                onRead = { 
                    if (isSelectionMode) onToggleSelection(chapter.id) 
                    else onChapterClick(chapter) 
                },
                onDownload = { onDownloadChapter(chapter) },
                isHttpSource = manga.source != 0L,
                downloadState = state,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onLongClick = { onToggleSelection(chapter.id) }
            )
        }
    }
}

@Composable
fun MangaActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterItem(
    chapter: Chapter,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    isHttpSource: Boolean,
    downloadState: com.example.manga_readerver2.core.download.Download.State = com.example.manga_readerver2.core.download.Download.State.NOT_DOWNLOADED,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        if (isSelected) Color(0xFF1E88E5).copy(alpha = 0.3f) else Color.Transparent
    )
    
    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onRead,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val cleanName = com.example.manga_readerver2.core.utils.ChapterRecognition.getDisplayTitle(chapter.name)
                
                Text(
                    text = cleanName.ifEmpty { chapter.name },
                    color = if (chapter.read) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (cleanName != chapter.name && !chapter.name.equals(cleanName, ignoreCase = true)) {
                    Text(
                        text = chapter.name,
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                val dateStr = try {
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(chapter.dateUpload))
                } catch(e: Exception) { "" }
                Text(
                    text = if (dateStr.isNotEmpty()) dateStr else "Unknown Date",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            if (isHttpSource && !isSelectionMode) {
                IconButton(
                    onClick = { if (downloadState == com.example.manga_readerver2.core.download.Download.State.NOT_DOWNLOADED) onDownload() },
                    modifier = Modifier.size(32.dp)
                ) {
                    when (downloadState) {
                        com.example.manga_readerver2.core.download.Download.State.DOWNLOADED -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Đã tải",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        com.example.manga_readerver2.core.download.Download.State.DOWNLOADING,
                        com.example.manga_readerver2.core.download.Download.State.QUEUE -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PrimaryOrange,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.DownloadForOffline,
                                contentDescription = "Tải xuống",
                                tint = Color.Gray.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MangaCoverDialog(
    manga: Manga,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = manga,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                contentScale = ContentScale.Fit
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChapterSettingsDialog(
    currentSort: com.example.manga_readerver2.features.detail.ChapterSort,
    currentFilter: com.example.manga_readerver2.features.detail.ChapterFilter,
    onDismiss: () -> Unit,
    onSortChange: (com.example.manga_readerver2.features.detail.ChapterSort) -> Unit,
    onFilterChange: (com.example.manga_readerver2.features.detail.ChapterFilter) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt chương", color = Color.White) },
        text = {
            Column {
                Text("Sắp xếp theo", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
                Row {
                    FilterChip(
                        selected = currentSort == com.example.manga_readerver2.features.detail.ChapterSort.LATEST,
                        onClick = { onSortChange(com.example.manga_readerver2.features.detail.ChapterSort.LATEST) },
                        label = { Text("Mới nhất") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = currentSort == com.example.manga_readerver2.features.detail.ChapterSort.OLDEST,
                        onClick = { onSortChange(com.example.manga_readerver2.features.detail.ChapterSort.OLDEST) },
                        label = { Text("Cũ nhất") }
                    )
                }
                
                Text("Lọc theo", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentFilter == com.example.manga_readerver2.features.detail.ChapterFilter.ALL,
                        onClick = { onFilterChange(com.example.manga_readerver2.features.detail.ChapterFilter.ALL) },
                        label = { Text("Tất cả") }
                    )
                    FilterChip(
                        selected = currentFilter == com.example.manga_readerver2.features.detail.ChapterFilter.UNREAD,
                        onClick = { onFilterChange(com.example.manga_readerver2.features.detail.ChapterFilter.UNREAD) },
                        label = { Text("Chưa đọc") }
                    )
                    FilterChip(
                        selected = currentFilter == com.example.manga_readerver2.features.detail.ChapterFilter.DOWNLOADED,
                        onClick = { onFilterChange(com.example.manga_readerver2.features.detail.ChapterFilter.DOWNLOADED) },
                        label = { Text("Đã tải") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = PrimaryOrange)
            }
        },
        containerColor = Color(0xFF2B2B2B),
        titleContentColor = Color.White
    )
}

@Composable
fun MangaDetailBottomActionBar(
    selectedCount: Int,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1E1E1E),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMarkRead)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("Đã đọc", color = Color.White, fontSize = 10.sp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMarkUnread)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.RemoveDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("Chưa đọc", color = Color.White, fontSize = 10.sp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDownload)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("Tải xuống", color = Color.White, fontSize = 10.sp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFCF6679), modifier = Modifier.size(22.dp))
                Text("Xóa tải về", color = Color(0xFFCF6679), fontSize = 10.sp)
            }
        }
    }
}@Composable
fun CategorySelectionDialog(
    allCategories: List<Category>,
    selectedIds: List<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    onUnfavorite: () -> Unit
) {
    var currentSelected by remember { mutableStateOf(selectedIds) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Categories") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (allCategories.isEmpty()) {
                    Text("No categories created yet.", color = Color.Gray)
                } else {
                    allCategories.forEach { category ->
                        val isChecked = currentSelected.contains(category.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelected = if (isChecked) {
                                        currentSelected - category.id
                                    } else {
                                        currentSelected + category.id
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                            )
                            Text(
                                text = category.name,
                                color = Color.White,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelected) }) {
                Text("Confirm", color = PrimaryOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onUnfavorite) {
                Text("Unfavorite", color = Color.Red)
            }
        }
    )
}
