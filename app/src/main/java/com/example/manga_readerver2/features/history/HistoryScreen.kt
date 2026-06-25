package com.example.manga_readerver2.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { screenModel.setSearchQuery(it) },
                                placeholder = { Text("TĂ¬m trong lá»‹ch sá»­...", color = Color.White.copy(alpha = 0.5f)) },
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
                            Text("Lá»‹ch sá»­", color = Color.White, fontWeight = FontWeight.ExtraBold)
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
                        IconButton(onClick = { screenModel.clearAllHistory() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "XĂ³a táº¥t cáº£", tint = Color.White)
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
                        "Lá»‹ch sá»­ trá»‘ng", 
                        "CĂ¡c truyá»‡n báº¡n Ä‘Ă£ Ä‘á»c sáº½ xuáº¥t hiá»‡n táº¡i Ä‘Ă¢y."
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
                                isSameDay(calendar, today) -> "HĂ´m nay"
                                isSameDay(calendar, yesterday) -> "HĂ´m qua"
                                else -> SimpleDateFormat("dd MMMM yyyy", Locale("vi")).format(Date(item.lastRead))
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedHistory.forEach { (date, items) ->
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

                            items(items, key = { it.chapterId }) { item ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            screenModel.deleteHistoryItem(item.mangaId)
                                            true
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
                                                    color = androidx.compose.ui.graphics.Color(0xFFB00020),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "XĂ³a",
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.padding(end = 20.dp)
                                            )
                                        }
                                    }
                                ) {
                                    HistoryCard(
                                        item = item,
                                        onClick = {
                                            rootNavigator.push(com.example.manga_readerver2.features.detail.MangaDetailScreen(item.mangaId))
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
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun HistoryCard(item: History, onClick: () -> Unit) {
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
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
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
                    fontWeight = FontWeight.Medium
                )
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.lastRead)),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

