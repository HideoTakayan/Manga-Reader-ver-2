package com.example.manga_readerver2.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.example.manga_readerver2.domain.model.History
import com.example.manga_readerver2.ui.theme.*
import com.example.manga_readerver2.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.*

// ── Dialog trạng thái ──────────────────────────────────────────
sealed interface HistoryDialog {
    data class DeleteItem(val mangaId: Long, val chapterId: Long) : HistoryDialog
    data object DeleteAll : HistoryDialog
}

class HistoryScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent ?: navigator
        val screenModel = rememberScreenModel { HistoryScreenModel() }
        val history by screenModel.history.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val searchQuery by screenModel.searchQuery.collectAsState()

        var isSearchActive by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<HistoryDialog?>(null) }

        // ── Delete dialogs ───────────────────────────────────────
        when (val d = dialog) {
            is HistoryDialog.DeleteItem -> {
                HistoryDeleteDialog(
                    onDismiss = { dialog = null },
                    onDeleteItem = {
                        screenModel.deleteHistoryByChapterId(d.chapterId)
                        dialog = null
                    },
                    onDeleteAll = {
                        screenModel.deleteHistoryItem(d.mangaId) // xóa toàn bộ truyện này
                        dialog = null
                    }
                )
            }
            is HistoryDialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismiss = { dialog = null },
                    onConfirm = {
                        screenModel.clearAllHistory()
                        dialog = null
                    }
                )
            }
            null -> Unit
        }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { screenModel.setSearchQuery(it) },
                                placeholder = { Text("Tìm trong lịch sử...", color = Color.White.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    cursorColor = PrimaryOrange,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Lịch sử", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) screenModel.setSearchQuery("")
                        }) {
                            Icon(
                                if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { dialog = HistoryDialog.DeleteAll }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Xóa tất cả", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryOrange)
                } else if (history.isEmpty()) {
                    EmptyState(
                        Icons.Default.History,
                        "Lịch sử trống",
                        "Các truyện bạn đã đọc sẽ xuất hiện tại đây."
                    )
                } else {
                    val filteredHistory = if (searchQuery.isBlank()) {
                        history
                    } else {
                        history.filter { it.mangaTitle.contains(searchQuery, ignoreCase = true) }
                    }

                    val groupedHistory = remember(filteredHistory) {
                        filteredHistory.groupBy { item ->
                            val calendar = Calendar.getInstance().apply { timeInMillis = item.lastRead }
                            val today = Calendar.getInstance()
                            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

                            when {
                                isSameDay(calendar, today) -> "Hôm nay"
                                isSameDay(calendar, yesterday) -> "Hôm qua"
                                else -> SimpleDateFormat("dd MMMM yyyy", Locale("vi")).format(Date(item.lastRead))
                            }
                        }
                    }

                    if (filteredHistory.isEmpty()) {
                        EmptyState(Icons.Default.SearchOff, "Không tìm thấy", "Không có mục lịch sử nào khớp với \"$searchQuery\"")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedHistory.forEach { (date, dateItems) ->
                                stickyHeader(key = "header-$date") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BackgroundDark)
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = date,
                                            color = PrimaryOrange,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }

                                items(dateItems, key = { it.chapterId }) { item ->
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                                dialog = HistoryDialog.DeleteItem(item.mangaId, item.chapterId)
                                                false // Không dismiss ngay, đợi user xác nhận
                                            } else false
                                        }
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 4.dp)
                                                    .background(
                                                        color = Color(0xFFB00020),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Xóa",
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(end = 20.dp)
                                                )
                                            }
                                        }
                                    ) {
                                        val isFavorite by screenModel.isInLibrary(item.mangaId).collectAsState(initial = true)

                                        HistoryCard(
                                            item = item,
                                            isFavorite = isFavorite,
                                            onClick = {
                                                rootNavigator.push(com.example.manga_readerver2.features.reader.ReaderScreen(item.mangaId, item.chapterId))
                                            },
                                            onCoverClick = {
                                                rootNavigator.push(com.example.manga_readerver2.features.detail.MangaDetailScreen(item.mangaId))
                                            },
                                            onDeleteClick = {
                                                dialog = HistoryDialog.DeleteItem(item.mangaId, item.chapterId)
                                            },
                                            onFavoriteClick = {
                                                screenModel.addToLibrary(item.mangaId)
                                            },
                                            isJsSource = screenModel.isJsSource(item.sourceId)
                                        )
                                    }
                                }
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

// ── Dialog xác nhận xóa 1 truyện ───────────────────────────────
@Composable
fun HistoryDeleteDialog(
    onDismiss: () -> Unit,
    onDeleteItem: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var deleteAllHistory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xóa lịch sử") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bạn muốn xóa lịch sử đọc truyện này?")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { deleteAllHistory = !deleteAllHistory }
                ) {
                    Checkbox(
                        checked = deleteAllHistory,
                        onCheckedChange = { deleteAllHistory = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xóa toàn bộ lịch sử của truyện này", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (deleteAllHistory) onDeleteAll() else onDeleteItem()
            }) {
                Text("Xóa", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

// ── Dialog xác nhận xóa tất cả ─────────────────────────────────
@Composable
fun HistoryDeleteAllDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xóa toàn bộ lịch sử") },
        text = { Text("Bạn có chắc muốn xóa toàn bộ lịch sử đọc? Hành động này không thể hoàn tác.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Xóa tất cả", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

// ── HistoryCard ─────────────────────────────────────────────────
@Composable
fun HistoryCard(
    item: History,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onCoverClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    isJsSource: Boolean
) {
    val fakeManga = remember(item) {
        com.example.manga_readerver2.domain.model.Manga(
            id = item.mangaId,
            source = item.sourceId,
            url = "",
            title = item.mangaTitle,
            thumbnailUrl = item.thumbnailUrl
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Cover ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(50.dp, 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCoverClick() }
            ) {
                AsyncImage(
                    model = fakeManga,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isJsSource) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(Color(0xFF1565C0).copy(alpha = 0.85f), RoundedCornerShape(bottomEnd = 4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("JS", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Text info ────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.mangaTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    item.chapterName,
                    color = PrimaryOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.lastRead)),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
            }

            // ── Nút Yêu thích (chỉ hiện nếu chưa trong thư viện) ──
            if (!isFavorite) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = "Thêm vào thư viện",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── Nút Xóa ──────────────────────────────────────
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Xóa",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
