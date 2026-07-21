package com.example.manga_readerver2.features.migration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.CatalogueSource
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

data class MigrateTargetScreen(val oldMangaId: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateTargetScreenModel() }
        val oldManga by screenModel.oldManga.collectAsState()
        val sources by screenModel.sources.collectAsState()
        val selectedSource by screenModel.selectedSource.collectAsState()
        val searchResults by screenModel.searchResults.collectAsState()
        val isSearching by screenModel.isSearching.collectAsState()
        
        var showMigrateDialog by remember { mutableStateOf(false) }
        var selectedTargetManga by remember { mutableStateOf<Manga?>(null) }

        LaunchedEffect(oldMangaId) {
            screenModel.init(oldMangaId)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedSource == null) "Chọn nguồn mới" else "Kết quả: ${oldManga?.title}",
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedSource != null) {
                                screenModel.clearSelectedSource()
                            } else {
                                navigator.pop()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
                )
            },
            containerColor = Color(0xFF121212)
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (oldManga == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = com.example.manga_readerver2.ui.theme.PrimaryOrange)
                } else if (selectedSource == null) {
                    // Kích hoạt giao diện danh mục nguồn truyện (Source List)
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sources) { source ->
                            ListItem(
                                headlineContent = { Text(source.name, color = Color.White) },
                                supportingContent = { Text(source.lang.uppercase(), color = Color.Gray) },
                                modifier = Modifier.clickable {
                                    screenModel.selectSource(source)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                } else {
                    // Hiển thị giao diện kết quả tìm kiếm (Search Results)
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = com.example.manga_readerver2.ui.theme.PrimaryOrange)
                    } else if (searchResults.isEmpty()) {
                        Text("Không tìm thấy kết quả phù hợp", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchResults) { targetManga ->
                                MigrateMangaCard(
                                    manga = targetManga,
                                    onClick = {
                                        selectedTargetManga = targetManga
                                        showMigrateDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showMigrateDialog && selectedTargetManga != null && oldManga != null) {
                MigrateDialog(
                    oldManga = oldManga!!,
                    newManga = selectedTargetManga!!,
                    onDismiss = { showMigrateDialog = false },
                    onConfirm = { copyRead, copyCat, delOld ->
                        screenModel.migrate(selectedTargetManga!!, copyRead, copyCat, delOld)
                        showMigrateDialog = false
                        // Điều hướng quay lại màn hình chính (Home Screen)
                        navigator.popUntilRoot()
                    }
                )
            }
        }
    }
}

@Composable
fun MigrateMangaCard(manga: Manga, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = manga,
            contentDescription = manga.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = manga.title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}
