package com.example.manga_readerver2.features.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.features.detail.MangaDetailScreen
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import eu.kanade.tachiyomi.source.CatalogueSource

enum class GlobalSearchFilter {
    PINNED, ALL, HAS_RESULTS
}

class GlobalSearchScreen(val initialQuery: String = "") : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { GlobalSearchScreenModel() }
        val state by screenModel.state.collectAsState()
        val pinnedSources by screenModel.pinnedSources.collectAsState()
        val scope = rememberCoroutineScope()
        
        var query by remember { mutableStateOf(initialQuery) }
        var selectedFilter by remember { mutableStateOf(GlobalSearchFilter.ALL) }

        LaunchedEffect(Unit) {
            if (initialQuery.isNotBlank()) {
                screenModel.search(initialQuery)
            }
        }

        Scaffold(
            topBar = {
                Surface(
                    color = Color(0xFF121212),
                    tonalElevation = 4.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Tìm kiếm...", color = Color.Gray) },
                                modifier = Modifier.weight(1f),
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
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    screenModel.search(query)
                                }),
                            )
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilter == GlobalSearchFilter.PINNED,
                                onClick = { selectedFilter = GlobalSearchFilter.PINNED },
                                label = { Text("📌 Đã ghim") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryOrange.copy(alpha = 0.2f),
                                    selectedLabelColor = PrimaryOrange
                                )
                            )
                            FilterChip(
                                selected = selectedFilter == GlobalSearchFilter.ALL,
                                onClick = { selectedFilter = GlobalSearchFilter.ALL },
                                label = { Text("✓ Tất cả") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryOrange.copy(alpha = 0.2f),
                                    selectedLabelColor = PrimaryOrange
                                )
                            )
                            FilterChip(
                                selected = selectedFilter == GlobalSearchFilter.HAS_RESULTS,
                                onClick = { selectedFilter = GlobalSearchFilter.HAS_RESULTS },
                                label = { Text("≡ Có kết quả") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryOrange.copy(alpha = 0.2f),
                                    selectedLabelColor = PrimaryOrange
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // LinearProgressIndicator tổng (giống Mihon)
                        if (state.isSearching || state.results.isNotEmpty()) {
                            val total = state.results.size
                            val done = state.results.values.count { it !is GlobalSearchResult.Loading }
                            if (state.isSearching && total > 0) {
                                LinearProgressIndicator(
                                    progress = { done.toFloat() / total.toFloat() },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PrimaryOrange,
                                    trackColor = PrimaryOrange.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF121212)
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var resultsList = state.results.toList()
                
                // Áp dụng bộ lọc
                resultsList = when (selectedFilter) {
                    GlobalSearchFilter.PINNED -> resultsList.filter { pinnedSources.contains(it.first.id.toString()) }
                    GlobalSearchFilter.HAS_RESULTS -> resultsList.filter { (_, result) -> 
                        result is GlobalSearchResult.Success && result.items.isNotEmpty() 
                    }
                    GlobalSearchFilter.ALL -> resultsList
                }
                
                resultsList = resultsList.sortedBy { (source, _) -> source.name }

                if (resultsList.isEmpty() && !state.isSearching && query.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nhập từ khóa để tìm kiếm", color = Color.Gray)
                        }
                    }
                } else if (resultsList.isEmpty() && !state.isSearching) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Không có nguồn nào phù hợp bộ lọc", color = Color.Gray)
                        }
                    }
                }

                items(resultsList, key = { it.first.id }) { (source, result) ->
                    SourceResultSection(
                        source = source,
                        result = result,
                        onMangaClick = { manga ->
                            scope.launch {
                                val id = screenModel.getMangaId(manga)
                                navigator.push(MangaDetailScreen(id))
                            }
                        },
                        onViewAllClick = {
                            if (result is GlobalSearchResult.Success && result.items.isNotEmpty()) {
                                navigator.push(CatalogueScreen(source.id, source.name, initialQuery = query))
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun SourceResultSection(
        source: CatalogueSource,
        result: GlobalSearchResult,
        onMangaClick: (Manga) -> Unit,
        onViewAllClick: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = result is GlobalSearchResult.Success && result.items.isNotEmpty()) { 
                        onViewAllClick()
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (result is GlobalSearchResult.Success && result.items.isNotEmpty()) {
                            Badge(
                                containerColor = PrimaryOrange,
                                contentColor = Color.White,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("${result.items.size}")
                            }
                        }
                    }
                    Text(
                        text = source.lang.uppercase(),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                
                when (result) {
                    is GlobalSearchResult.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp).padding(end = 4.dp), 
                            color = PrimaryOrange,
                            strokeWidth = 2.dp
                        )
                    }
                    is GlobalSearchResult.Success -> {
                        if (result.items.isNotEmpty()) {
                            Icon(
                                Icons.Default.ChevronRight, 
                                contentDescription = "Xem tất cả", 
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text("Không có kết quả", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    is GlobalSearchResult.Error -> {
                        Text("Lỗi", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
            
            if (result is GlobalSearchResult.Success && result.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(result.items, key = { it.url }) { manga ->
                        GlobalSearchMangaCard(manga, onMangaClick)
                    }
                }
            }
        }
    }

    @Composable
    fun GlobalSearchMangaCard(manga: Manga, onClick: (Manga) -> Unit) {
        Column(
            modifier = Modifier
                .width(100.dp)
                .clickable { onClick(manga) }
        ) {
            AsyncImage(
                model = manga,
                contentDescription = manga.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(6.dp)),
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
}


