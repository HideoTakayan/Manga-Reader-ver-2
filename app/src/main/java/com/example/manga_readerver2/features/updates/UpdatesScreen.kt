package com.example.manga_readerver2.features.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.example.manga_readerver2.core.download.Download
import com.example.manga_readerver2.domain.model.Update
import com.example.manga_readerver2.ui.components.EmptyState
import com.example.manga_readerver2.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

class UpdatesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { UpdatesScreenModel() }
        val updates by screenModel.updates.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val selectedIds by screenModel.selectedIds.collectAsState()
        val isSelectionMode by screenModel.isSelectionMode.collectAsState()
        val downloadStatus by screenModel.downloadStatus.collectAsState()
        val lastUpdated by screenModel.lastUpdated.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent ?: navigator

        var isRefreshing by remember { mutableStateOf(false) }

        // Bấm Back khi đang ở chế độ chọn nhiều → thoát mode
        BackHandler(enabled = isSelectionMode) {
            screenModel.clearSelection()
        }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                if (isSelectionMode) {
                    // ── AppBar Selection Mode ──────────────────
                    TopAppBar(
                        title = { Text("${selectedIds.size} đã chọn", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = { screenModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Hủy", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { screenModel.selectAll() }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Chọn tất cả", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
                    )
                } else {
                    // ── AppBar thường ──────────────────────────
                    TopAppBar(
                        title = { Text("Cập nhật", color = Color.White, fontWeight = FontWeight.ExtraBold) },
                        actions = {
                            if (updates.isNotEmpty()) {
                                IconButton(onClick = { screenModel.markAllRead() }) {
                                    Icon(Icons.Default.DoneAll, contentDescription = "Đánh dấu tất cả đã đọc", tint = Color.White)
                                }
                            }
                            IconButton(onClick = {
                                isRefreshing = true
                                screenModel.refresh()
                                isRefreshing = false
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                    )
                }
            },
            bottomBar = {
                // ── Bottom Action Bar (khi đang chọn nhiều) ──
                if (isSelectionMode && selectedIds.isNotEmpty()) {
                    UpdatesBottomActionBar(
                        selectedIds = selectedIds,
                        updates = updates,
                        downloadStatus = downloadStatus,
                        onMarkRead = { screenModel.markSelectedRead(true) },
                        onMarkUnread = { screenModel.markSelectedRead(false) },
                        onDownload = { screenModel.downloadSelected() },
                        onDeleteDownload = { screenModel.deleteDownloadSelected() }
                    )
                }
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    screenModel.refresh()
                    isRefreshing = false
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryOrange)
                    }
                } else if (updates.isEmpty()) {
                    EmptyState(Icons.Default.Update, "Không có cập nhật mới", "Các chương mới từ truyện bạn theo dõi sẽ xuất hiện tại đây.")
                } else {
                    val groupedUpdates = remember(updates) {
                        updates.groupBy { item ->
                            val calendar = Calendar.getInstance().apply { timeInMillis = item.dateFetch }
                            val today = Calendar.getInstance()
                            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                            when {
                                isSameDay(calendar, today) -> "Hôm nay"
                                isSameDay(calendar, yesterday) -> "Hôm qua"
                                else -> SimpleDateFormat("dd MMMM yyyy", Locale("vi")).format(Date(item.dateFetch))
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = if (isSelectionMode) 80.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Dòng "Cập nhật lần cuối"
                        if (lastUpdated > 0L) {
                            item(key = "last-updated") {
                                Text(
                                    text = "Đã cập nhật: ${relativeTime(lastUpdated)}",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        groupedUpdates.forEach { (date, dateItems) ->
                            stickyHeader(key = "header-$date") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark)
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = date,
                                        color = PrimaryOrange,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            items(dateItems, key = { "${it.mangaId}-${it.chapterId}" }) { update ->
                                val isSelected = update.chapterId in selectedIds
                                val downloadState = downloadStatus[update.chapterId]
                                    ?: Download.State.NOT_DOWNLOADED

                                UpdateCard(
                                    update = update,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    downloadState = downloadState,
                                    onClick = {
                                        if (isSelectionMode) {
                                            screenModel.toggleSelection(update.chapterId)
                                        } else {
                                            rootNavigator.push(
                                                com.example.manga_readerver2.features.detail.MangaDetailScreen(update.mangaId)
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        screenModel.toggleSelection(update.chapterId)
                                    },
                                    onDownloadClick = {
                                        screenModel.downloadChapter(update)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "vừa xong"
        minutes < 60 -> "$minutes phút trước"
        minutes < 1440 -> "${minutes / 60} giờ trước"
        else -> "${minutes / 1440} ngày trước"
    }
}

// ── UpdateCard ──────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpdateCard(
    update: Update,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    downloadState: Download.State,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val bgColor = if (isSelected) PrimaryOrange.copy(alpha = 0.15f) else Color.Transparent
    val textAlpha = if (update.read) 0.45f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox hoặc cover
        Box(modifier = Modifier.size(44.dp, 60.dp)) {
            AsyncImage(
                model = com.example.manga_readerver2.domain.model.Manga(
                    id = update.mangaId,
                    source = 0L,
                    url = "",
                    title = update.mangaTitle,
                    thumbnailUrl = update.thumbnailUrl
                ),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PrimaryOrange.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = update.mangaTitle,
                color = Color.White.copy(alpha = textAlpha),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Chấm tròn xanh = chưa đọc
                if (!update.read) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(PrimaryOrange, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (update.bookmark) {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = PrimaryOrange,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = update.chapterName,
                    color = Color.White.copy(alpha = textAlpha * 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Nút tải xuống
        if (!isSelectionMode) {
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(36.dp)
            ) {
                when (downloadState) {
                    Download.State.DOWNLOADED -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Đã tải",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Download.State.DOWNLOADING, Download.State.QUEUE -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryOrange,
                        strokeWidth = 2.dp
                    )
                    else -> Icon(
                        Icons.Default.DownloadForOffline,
                        contentDescription = "Tải xuống",
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Bottom Action Bar ───────────────────────────────────────────
@Composable
fun UpdatesBottomActionBar(
    selectedIds: Set<Long>,
    updates: List<Update>,
    downloadStatus: Map<Long, Download.State>,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit
) {
    val selectedUpdates = updates.filter { it.chapterId in selectedIds }
    val hasUnread = selectedUpdates.any { !it.read }
    val hasRead = selectedUpdates.any { it.read }
    val hasDownloaded = selectedUpdates.any { downloadStatus[it.chapterId] == Download.State.DOWNLOADED }
    val hasNotDownloaded = selectedUpdates.any { downloadStatus[it.chapterId] != Download.State.DOWNLOADED }

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
            if (hasUnread) {
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
            }
            if (hasRead) {
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
            }
            if (hasNotDownloaded) {
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
            }
            if (hasDownloaded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDeleteDownload)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFCF6679), modifier = Modifier.size(22.dp))
                    Text("Xóa tải về", color = Color(0xFFCF6679), fontSize = 10.sp)
                }
            }
        }
    }
}
