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

class GlobalSearchScreen(val initialQuery: String = "") : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { GlobalSearchScreenModel() }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()
        
        var query by remember { mutableStateOf(initialQuery) }

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Tìm kiếm toàn cầu...", color = Color.Gray) },
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
                }
            },
            containerColor = Color(0xFF121212)
        )
 { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val resultsList = state.results.toList()
                    .filter { (_, result) -> result !is GlobalSearchResult.Success || result.items.isNotEmpty() }
                    .sortedBy { (source, _) -> source.name }

                if (resultsList.isEmpty() && !state.isSearching) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nhập từ khóa để tìm kiếm trên tất cả nguồn", color = Color.Gray)
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
                            navigator.push(CatalogueScreen(source.id, source.name, initialQuery = query))
                        }
                    )
                }
                
                if (state.isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryOrange)
                        }
                    }
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
                    .clickable { 
                        onViewAllClick()
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.name,
                            color = Color.White,
                            fontSize = 16.sp,
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
                Icon(
                    Icons.Default.ChevronRight, 
                    contentDescription = "Xem tất cả", 
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            when (result) {
                is GlobalSearchResult.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PrimaryOrange)
                    }
                }
                is GlobalSearchResult.Error -> {
                    Text("Lỗi: ${result.message}", color = Color.Red, fontSize = 12.sp)
                }
                is GlobalSearchResult.Success -> {
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
    }

    @Composable
    fun GlobalSearchMangaCard(manga: Manga, onClick: (Manga) -> Unit) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .clickable { onClick(manga) }
        ) {
            AsyncImage(
                model = manga,
                contentDescription = manga.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
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
}


