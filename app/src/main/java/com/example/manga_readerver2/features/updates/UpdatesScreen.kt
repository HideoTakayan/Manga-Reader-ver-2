package com.example.manga_readerver2.features.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import com.example.manga_readerver2.ui.components.EmptyState
import cafe.adriel.voyager.navigator.currentOrThrow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
class UpdatesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { UpdatesScreenModel() }
        val updates by screenModel.updates.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val navigator = cafe.adriel.voyager.navigator.LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent ?: navigator

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Cập nhật", color = Color.White, fontWeight = FontWeight.ExtraBold) },
                    actions = {
                        if (updates.isNotEmpty()) {
                            IconButton(onClick = { screenModel.markAllRead() }) {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = "Đánh dấu tất cả đã đọc",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryOrange)
                } else if (updates.isEmpty()) {
                    EmptyState(Icons.Default.Update, "Không có cập nhật mới", "Các chương mới từ truyện bạn theo dõi sẽ xuất hiện tại đây.")
                } else {
                    // VIP: Group updates by date
                    val groupedUpdates = remember(updates) {
                        updates.groupBy { item ->
                            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = item.dateUpload }
                            val today = java.util.Calendar.getInstance()
                            val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                            
                            when {
                                isSameDay(calendar, today) -> "Hôm nay"
                                isSameDay(calendar, yesterday) -> "Hôm qua"
                                else -> java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("vi")).format(java.util.Date(item.dateUpload))
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedUpdates.forEach { (date, items) ->
                            stickyHeader {
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

                            items(items) { update ->
                                UpdateCard(
                                    update = update,
                                    onClick = { rootNavigator.push(com.example.manga_readerver2.features.detail.MangaDetailScreen(update.mangaId)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isSameDay(cal1: java.util.Calendar, cal2: java.util.Calendar): Boolean {
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

@Composable
fun UpdateCard(update: com.example.manga_readerver2.domain.model.Update, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(
                modifier = Modifier.size(50.dp, 70.dp), 
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    model = update.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    update.mangaTitle, 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    update.chapterName, 
                    color = PrimaryOrange, 
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (update.read) "Đã xem" else "Mới",
                    color = if (update.read) Color.Gray else PrimaryOrange,
                    fontSize = 10.sp,
                    fontWeight = if (update.read) FontWeight.Normal else FontWeight.Bold
                )
            }
            IconButton(onClick = { /* Download */ }) {
                Icon(
                    Icons.Default.DownloadForOffline, 
                    contentDescription = "Tải xuống", 
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
