@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
 
package com.example.manga_readerver2.features.library

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.ui.theme.*
import com.example.manga_readerver2.ui.components.EmptyState
import com.example.manga_readerver2.features.library.components.LibrarySettingsDialog
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import com.example.manga_readerver2.core.preference.LibraryPreferences

class LibraryScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { LibraryScreenModel() }
        val libraryItems by screenModel.libraryItems.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val searchQuery by screenModel.searchQuery.collectAsState()
        val displayMode by screenModel.displayMode.collectAsState()
        
        val preferences = Injekt.get<LibraryPreferences>()
        val showDownloadBadges by preferences.showDownloadBadges.asFlow().collectAsState(preferences.showDownloadBadges.get())
        val showUnreadBadges by preferences.showUnreadBadges.asFlow().collectAsState(preferences.showUnreadBadges.get())
        
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent ?: navigator

        var isSearchActive by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        
        val categories by screenModel.categories.collectAsState()
        val selectedCategoryIndex by screenModel.selectedCategoryIndex.collectAsState()
        
        val selectedMangaIds by screenModel.selectedMangaIds.collectAsState()
        val isSelectionMode = selectedMangaIds.isNotEmpty()

        val errorMessage by screenModel.errorMessage.collectAsState()
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

        LaunchedEffect(errorMessage) {
            errorMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                screenModel.clearErrorMessage()
            }
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        val importFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { screenModel.importManga(context, it) }
        }

        val importFolderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { screenModel.importFolder(context, it) }
        }

        Scaffold(
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = isSearchActive,
                            transitionSpec = {
                                fadeIn() + expandHorizontally() togetherWith fadeOut() + shrinkHorizontally()
                            },
                            label = "SearchTransition"
                        ) { searching ->
                            if (searching) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { screenModel.setSearchQuery(it) },
                                    placeholder = { Text("Tìm trong thư viện...", color = Color.White.copy(alpha = 0.5f)) },
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Thư viện", color = Color.White, fontWeight = FontWeight.ExtraBold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = "${libraryItems.size}",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
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
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Bộ lọc", tint = Color.White)
                        }
                        
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Thêm", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                containerColor = Color(0xFF2B2B2B)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Cập nhật thư viện", color = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        screenModel.refreshLibrary() // Global Update
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Cập nhật danh mục", color = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        // Refresh current category only
                                        screenModel.refreshCategory()
                                    }
                                )
                                 DropdownMenuItem(
                                    text = { Text("Mở truyện ngẫu nhiên", color = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        screenModel.getRandomManga()?.let { 
                                            rootNavigator.push(com.example.manga_readerver2.features.detail.MangaDetailScreen(it)) 
                                        }
                                    }
                                )
                                 DropdownMenuItem(
                                    text = { Text("Chỉnh sửa danh mục", color = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        rootNavigator.push(CategoryManagerScreen())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nhập tệp truyện (EPUB, PDF, ZIP)", color = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        // Chỉ chấp nhận EPUB, PDF, ZIP, CBZ để tránh import file không hợp lệ
                                        importFileLauncher.launch(arrayOf(
                                            "application/epub+zip",
                                            "application/pdf",
                                            "application/zip",
                                            "application/x-cbz",
                                            "application/octet-stream",  // Fallback cho file không có MIME đúng
                                            "*/*"  // Giữ lại */* ở cuối vì một số file manager cần
                                        ))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nhập thư mục ảnh (Folder)", color = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        importFolderLauncher.launch(null)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )

                // Selection TopBar Overlay
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    var showCategoryDialog by remember { mutableStateOf(false) }

                    SelectionTopBar(
                        selectedCount = selectedMangaIds.size,
                        onClose = { screenModel.clearSelection() },
                        onSelectAll = { screenModel.selectAll() },
                        onInvertSelection = { screenModel.invertSelection() }
                    )
                }
            }
        ) { paddingValues ->
            var showCategoryDialog by remember { mutableStateOf(false) }
            var showDeleteDialog by remember { mutableStateOf(false) }

            if (showCategoryDialog) {
                AlertDialog(
                    onDismissRequest = { showCategoryDialog = false },
                    title = { Text("Thêm vào danh mục", fontWeight = FontWeight.Bold) },
                    text = {
                        LazyColumn {
                            items(categories) { category ->
                                TextButton(
                                    onClick = {
                                        screenModel.bulkChangeCategory(category.id)
                                        showCategoryDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(category.name, color = PrimaryOrange, fontSize = 16.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCategoryDialog = false }) {
                            Text("Hủy", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF2B2B2B),
                    titleContentColor = Color.White
                )
            }

            if (showDeleteDialog) {
                var deleteDownloads by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Xóa khỏi thư viện?", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Truyện này sẽ bị xóa khỏi thư viện cá nhân của bạn.", color = Color.White.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteDownloads = !deleteDownloads }
                            ) {
                                Checkbox(
                                    checked = deleteDownloads,
                                    onCheckedChange = { deleteDownloads = it },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                                )
                                Text("Đồng thời xóa các chương đã tải", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            showDeleteDialog = false
                            screenModel.bulkUnfollow(deleteDownloads = deleteDownloads)
                        }) {
                            Text("Đồng ý", color = PrimaryOrange)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Hủy", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF2B2B2B),
                    titleContentColor = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = if (isSelectionMode) paddingValues.calculateBottomPadding() + 80.dp else paddingValues.calculateBottomPadding()
                    )
            ) {
                Column {
                    if (categories.isNotEmpty() && !isSearchActive) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedCategoryIndex,
                            containerColor = BackgroundDark,
                            contentColor = PrimaryOrange,
                            edgePadding = 16.dp,
                            divider = {},
                            indicator = { tabPositions ->
                                if (selectedCategoryIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCategoryIndex]),
                                        color = PrimaryOrange
                                    )
                                }
                            }
                        ) {
                            categories.forEachIndexed { index, category ->
                                Tab(
                                    selected = selectedCategoryIndex == index,
                                    onClick = { screenModel.setSelectedCategory(index) },
                                    text = { 
                                        Text(
                                            category.name, 
                                            fontSize = 14.sp,
                                            fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal
                                        ) 
                                    },
                                    unselectedContentColor = TextSecondary
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        PullToRefreshBox(
                            isRefreshing = isLoading,
                            onRefresh = { screenModel.refreshCategory() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LibraryContent(
                                mangaList = libraryItems,
                                displayMode = displayMode,
                                showDownloadBadges = showDownloadBadges,
                                showUnreadBadges = showUnreadBadges,
                                selectedIds = selectedMangaIds,
                                onMangaClick = { mangaId ->
                                    if (isSelectionMode) {
                                        screenModel.toggleSelection(mangaId)
                                    } else {
                                        rootNavigator.push(com.example.manga_readerver2.features.detail.MangaDetailScreen(mangaId))
                                    }
                                },
                                onMangaLongClick = { mangaId ->
                                    screenModel.toggleSelection(mangaId)
                                }
                            )
                        }
                    }
                }

                if (showSettingsDialog) {
                    LibrarySettingsDialog(
                        onDismissRequest = { showSettingsDialog = false }
                    )
                }

                // Selection Bottom Action Bar
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SelectionBottomBar(
                        onUpdate = { screenModel.bulkUpdate() },
                        onMarkRead = { read -> screenModel.bulkMarkRead(read) },
                        onChangeCategory = { showCategoryDialog = true },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryContent(
    mangaList: List<LibraryItem>,
    displayMode: LibraryDisplayMode,
    showDownloadBadges: Boolean,
    showUnreadBadges: Boolean,
    selectedIds: Set<Long>,
    onMangaClick: (Long) -> Unit,
    onMangaLongClick: (Long) -> Unit
) {
    if (mangaList.isEmpty()) {
        EmptyState(Icons.Default.FavoriteBorder, "Thư viện trống", "Hãy thêm truyện yêu thích để theo dõi tại đây.")
        return
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mangaList) { item ->
                    LibraryListItem(
                        item = item, 
                        showDownloadBadges = showDownloadBadges, 
                        showUnreadBadges = showUnreadBadges, 
                        isSelected = selectedIds.contains(item.manga.id),
                        onClick = { onMangaClick(item.manga.id) },
                        onLongClick = { onMangaLongClick(item.manga.id) }
                    )
                }
            }
        }
        else -> {
            val columns = when (displayMode) {
                LibraryDisplayMode.CompactGrid -> 3
                LibraryDisplayMode.ComfortGrid -> 2
                LibraryDisplayMode.CoverOnlyGrid -> 3
                else -> 3
            }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mangaList) { item ->
                    LibraryGridItem(
                        item = item,
                        displayMode = displayMode,
                        showDownloadBadges = showDownloadBadges,
                        showUnreadBadges = showUnreadBadges,
                        isSelected = selectedIds.contains(item.manga.id),
                        onClick = { onMangaClick(item.manga.id) },
                        onLongClick = { onMangaLongClick(item.manga.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryGridItem(
    item: LibraryItem,
    displayMode: LibraryDisplayMode,
    showDownloadBadges: Boolean,
    showUnreadBadges: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val manga = item.manga
    Column(modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box {
            Card(
                modifier = Modifier.aspectRatio(0.7f).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                val sharedTransitionScope = com.example.manga_readerver2.core.navigation.LocalSharedTransitionScope.current
                val animatedVisibilityScope = com.example.manga_readerver2.core.navigation.LocalNavAnimatedVisibilityScope.current
                
                AsyncImage(
                    model = manga,
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize().let {
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                it.sharedElement(
                                    rememberSharedContentState(key = "manga_cover_${manga.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        } else it
                    },
                    contentScale = ContentScale.Crop
                )
                
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PrimaryOrange.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PrimaryOrange,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            // Badges
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                if (showUnreadBadges && item.unreadCount > 0) {
                    Badge(item.unreadCount.toString(), PrimaryOrange)
                }
            }
            
            if (showDownloadBadges && item.isDownloaded) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                ) {
                    Badge("DL", Color(0xFF4CAF50).copy(alpha = 0.9f))
                }
            }
            
            if (displayMode == LibraryDisplayMode.CoverOnlyGrid) {
                // Gradient overlay for title
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 300f
                            )
                        )
                )
                Text(
                    text = manga.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                )
            }
        }
        
        if (displayMode != LibraryDisplayMode.CoverOnlyGrid) {
            val titleSize = if (displayMode == LibraryDisplayMode.ComfortGrid) 14.sp else 12.sp
            val padding = if (displayMode == LibraryDisplayMode.ComfortGrid) 8.dp else 4.dp
            
            Spacer(modifier = Modifier.height(padding))
            Text(
                text = manga.title,
                color = Color.White,
                fontSize = titleSize,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun LibraryListItem(
    item: LibraryItem,
    showDownloadBadges: Boolean,
    showUnreadBadges: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val manga = item.manga
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) PrimaryOrange.copy(alpha = 0.1f) else Color.Transparent)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.size(width = 50.dp, height = 70.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            val sharedTransitionScope = com.example.manga_readerver2.core.navigation.LocalSharedTransitionScope.current
            val animatedVisibilityScope = com.example.manga_readerver2.core.navigation.LocalNavAnimatedVisibilityScope.current

            AsyncImage(
                model = manga,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().let {
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            it.sharedElement(
                                rememberSharedContentState(key = "manga_cover_${manga.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else it
                },
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(manga.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(manga.author ?: "Không rõ tác giả", color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showUnreadBadges && item.unreadCount > 0) Badge(item.unreadCount.toString(), PrimaryOrange)
            if (showDownloadBadges && item.isDownloaded) Badge("DL", Color(0xFF4CAF50))
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(
        color = color,
        shape = CircleShape,
    ) {
        Text(
            text = text, 
            color = Color.White, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    Surface(
        color = BackgroundDark,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("$selectedCount đã chọn", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Chọn tất cả", tint = Color.White)
                }
                IconButton(onClick = onInvertSelection) {
                    Icon(Icons.Default.Flip, contentDescription = "Đảo ngược", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
        )
    }
}

@Composable
fun SelectionBottomBar(
    onUpdate: () -> Unit,
    onMarkRead: (Boolean) -> Unit,
    onChangeCategory: () -> Unit,
    onDelete: () -> Unit
) {
    var showMarkMenu by remember { mutableStateOf(false) }

    BottomAppBar(
        containerColor = Color(0xFF2B2B2B),
        contentColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onUpdate)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Sync, contentDescription = "Cập nhật")
                    Text("Cập nhật", fontSize = 10.sp, maxLines = 1)
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMarkMenu = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Đánh dấu")
                        Text("Đánh dấu", fontSize = 10.sp, maxLines = 1)
                    }
                }
                DropdownMenu(
                    expanded = showMarkMenu,
                    onDismissRequest = { showMarkMenu = false },
                    containerColor = Color(0xFF2B2B2B)
                ) {
                    DropdownMenuItem(
                        text = { Text("Đã đọc", color = Color.White) },
                        onClick = {
                            showMarkMenu = false
                            onMarkRead(true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Chưa đọc", color = Color.White) },
                        onClick = {
                            showMarkMenu = false
                            onMarkRead(false)
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onChangeCategory)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Label, contentDescription = "Danh mục")
                    Text("Danh mục", fontSize = 10.sp, maxLines = 1)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onDelete)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, contentDescription = "Xóa")
                    Text("Xóa", fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

