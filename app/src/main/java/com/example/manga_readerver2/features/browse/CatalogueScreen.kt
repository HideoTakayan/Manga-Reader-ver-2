package com.example.manga_readerver2.features.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.example.manga_readerver2.features.detail.MangaDetailScreen
import com.example.manga_readerver2.ui.theme.*
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CatalogueScreen(val sourceId: Long, val sourceName: String, val latest: Boolean = false, val initialQuery: String? = null) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { CatalogueScreenModel() }
        
        var isSearchMode by remember { mutableStateOf(!initialQuery.isNullOrEmpty()) }
        var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
        var showFilterSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        LaunchedEffect(sourceId) {
            if (latest) {
                screenModel.setListing(CatalogueScreenModel.Listing.Latest)
            } else if (!initialQuery.isNullOrEmpty()) {
                screenModel.search(initialQuery)
            }
            screenModel.initSource(sourceId)
        }

        // Tạo luồng dữ liệu mới mỗi khi state thay đổi
        val listing by screenModel.listing.collectAsState()
        val query by screenModel.searchQuery.collectAsState()
        val filters by screenModel.filters.collectAsState()
        
        val pagingData = screenModel.mangaFlow.collectAsLazyPagingItems()

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                if (isSearchMode) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Tìm kiếm...") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { screenModel.search(searchQuery) }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                isSearchMode = false
                                searchQuery = ""
                                screenModel.search("") 
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { searchQuery = ""; screenModel.search("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                    )
                } else {
                    TopAppBar(
                        title = { Text(sourceName, color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchMode = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Thanh Chips: Phổ biến / Mới nhất
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = listing == CatalogueScreenModel.Listing.Popular,
                        onClick = { screenModel.setListing(CatalogueScreenModel.Listing.Popular) },
                        label = { Text("Phổ biến") }
                    )
                    FilterChip(
                        selected = listing == CatalogueScreenModel.Listing.Latest,
                        onClick = { screenModel.setListing(CatalogueScreenModel.Listing.Latest) },
                        label = { Text("Mới cập nhật") }
                    )
                }

                // Grid Manga Paging
                @OptIn(ExperimentalMaterial3Api::class)
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = pagingData.loadState.refresh is LoadState.Loading,
                    onRefresh = { pagingData.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pagingData.loadState.refresh is LoadState.Loading && pagingData.itemCount == 0) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pagingData.itemCount) { index ->
                                val manga = pagingData[index]
                                if (manga != null) {
                                    Column(modifier = Modifier.clickable { navigator.push(MangaDetailScreen(manga.id)) }) {
                                        Card(
                                            modifier = Modifier.aspectRatio(0.7f).fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            AsyncImage(
                                                model = manga,
                                                contentDescription = manga.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                        Text(
                                            text = manga.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Append loading
                            if (pagingData.loadState.append is LoadState.Loading) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showFilterSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFilterSheet = false },
                    sheetState = sheetState,
                    containerColor = BackgroundDark,
                    contentColor = Color.White
                ) {
                    FilterContent(
                        filters = filters,
                        onReset = { screenModel.resetFilters() },
                        onFilter = {
                            screenModel.setFilters(filters)
                            showFilterSheet = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun FilterContent(
        filters: FilterList,
        onReset: () -> Unit,
        onFilter: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.8f).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bộ lọc", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = onReset) { Text("Đặt lại", color = PrimaryOrange) }
                    Button(
                        onClick = onFilter,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                    ) {
                        Text("Lọc")
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filters) { filter ->
                    FilterItem(filter)
                }
            }
        }
    }

    @Composable
    private fun FilterItem(filter: Filter<*>) {
        when (filter) {
            is Filter.Header -> {
                Text(
                    text = filter.name,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryOrange,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            is Filter.Separator -> {
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))
            }
            is Filter.CheckBox -> {
                var state by remember { mutableStateOf(filter.state) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { 
                        state = !state
                        filter.state = state
                    }
                ) {
                    Checkbox(
                        checked = state,
                        onCheckedChange = { 
                            state = it
                            filter.state = it
                        },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                    )
                    Text(filter.name)
                }
            }
            is Filter.TriState -> {
                var state by remember { mutableStateOf(filter.state) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        state = (state + 1) % 3
                        filter.state = state
                    }.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when(state) {
                            1 -> Icon(Icons.Default.CheckBox, contentDescription = null, tint = PrimaryOrange)
                            2 -> Icon(Icons.Default.IndeterminateCheckBox, contentDescription = null, tint = Color.Red)
                            else -> Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = filter.name,
                        color = when(state) {
                            1 -> Color.White
                            2 -> Color.Red.copy(alpha = 0.7f)
                            else -> Color.White.copy(alpha = 0.7f)
                        }
                    )
                }
            }
            is Filter.Select<*> -> {
                var state by remember { mutableStateOf(filter.state) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(filter.name, color = Color.Gray, fontSize = 12.sp)
                    // Ở đây có thể dùng ExposedDropdownMenu hoặc đơn giản là một Row cuộn ngang
                    // Để đơn giản tôi sẽ dùng một Row các FilterChips
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        filter.values.forEachIndexed { index, value ->
                            FilterChip(
                                selected = state == index,
                                onClick = { 
                                    state = index
                                    filter.state = index
                                },
                                label = { Text(value.toString()) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
            is Filter.Group<*> -> {
                var expanded by remember { mutableStateOf(false) }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(filter.name, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    if (expanded) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            filter.state.forEach { subFilter ->
                                if (subFilter is Filter<*>) {
                                    FilterItem(subFilter)
                                }
                            }
                        }
                    }
                }
            }
            is Filter.Text -> {
                var state by remember { mutableStateOf(filter.state) }
                TextField(
                    value = state,
                    onValueChange = { 
                        state = it
                        filter.state = it
                    },
                    label = { Text(filter.name) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                    )
                )
            }
            else -> {}
        }
    }
}
