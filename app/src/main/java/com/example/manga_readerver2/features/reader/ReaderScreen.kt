@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.manga_readerver2.features.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.features.reader.components.ReaderSettingsSheet
import com.example.manga_readerver2.features.reader.components.TtsPlayerBar
import com.example.manga_readerver2.features.reader.components.TtsSettingsDialog
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import com.example.manga_readerver2.ui.theme.CardBackground
import com.example.manga_readerver2.ui.theme.TextSecondary
import kotlinx.coroutines.launch

data class ReaderScreen(
    val mangaId: Long,
    val chapterId: Long
) : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { ReaderScreenModel() }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val chapters by screenModel.chapters.collectAsState()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ReaderDrawer(
                    chapters = chapters,
                    currentChapterId = chapterId,
                    onChapterClick = { chapter ->
                        scope.launch { drawerState.close() }
                        screenModel.navigateToChapter(chapter)
                    }
                )
            }
        ) {
            ReaderMainContent(screenModel, mangaId, chapterId, drawerState)
        }
    }
}

@Composable
fun ReaderMainContent(
    screenModel: ReaderScreenModel,
    mangaId: Long,
    chapterId: Long,
    drawerState: DrawerState
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()

    val pageCount by screenModel.pageCount.collectAsState()
    val currentPage by screenModel.currentPage.collectAsState()
    val allPages by screenModel.allPages.collectAsState()
    val readingMode by screenModel.readingMode.collectAsState()
    val isLoading by screenModel.isLoading.collectAsState()
    val errorMessage by screenModel.errorMessage.collectAsState()
    val isTextReader by screenModel.isTextReader.collectAsState()
    val chapterName by screenModel.chapterName.collectAsState()
    val scaleMode by screenModel.scaleMode.collectAsState()
    
    val orientation by screenModel.orientation.collectAsState()
    val readerTheme by screenModel.readerTheme.collectAsState()
    val isTtsPlaying by screenModel.isTtsPlaying.collectAsState()
    val keepScreenOn by screenModel.keepScreenOn.collectAsState()
    val filterMode by screenModel.colorFilterMode.collectAsState()
    val brightness by screenModel.brightness.collectAsState()

    val (bgColor, textColor) = when(readerTheme) {
        1 -> Color.White to Color.Black
        2 -> Color(0xFFF4ECD8) to Color(0xFF5B4636)
        else -> BackgroundDark to Color.White
    }

    val activity = context as? Activity
    LaunchedEffect(orientation) {
        activity?.requestedOrientation = when(orientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var showControls by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTtsPlayer by remember { mutableStateOf(false) }
    var showTtsSettings by remember { mutableStateOf(false) }

    // Tự động hiện TTS bar khi đọc truyện chữ (EPUB, VBook) - chỉ hiện lần đầu
    var hasAutoShownTts by remember { mutableStateOf(false) }
    LaunchedEffect(isTextReader) {
        if (isTextReader && !hasAutoShownTts) {
            showTtsPlayer = true
            hasAutoShownTts = true
        }
    }

    val actualPageCount = if (pageCount > 0) pageCount + 1 else 0
    val pagerState = rememberPagerState(pageCount = { actualPageCount })
    val listState = rememberLazyListState()

    LaunchedEffect(mangaId, chapterId) {
        screenModel.loadChapter(mangaId, chapterId)
    }

    LaunchedEffect(currentPage) {
        if (readingMode == ReadingMode.HORIZONTAL) {
            if (pageCount > 0 && pagerState.currentPage != currentPage && currentPage < pageCount && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(currentPage)
            }
        } else {
            if (pageCount > 0 && listState.firstVisibleItemIndex != currentPage && currentPage < pageCount && !listState.isScrollInProgress) {
                listState.animateScrollToItem(currentPage)
            }
        }
    }

    DisposableEffect(keepScreenOn) {
        val activity = context as? Activity
        if (keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Scaffold(
        containerColor = bgColor,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(color = PrimaryOrange, modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val density = androidx.compose.ui.platform.LocalDensity.current
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

                val handleTap: (Float, Float, Float) -> Unit = { x, _, width ->
                    when {
                        x < width / 3 -> {
                            if (isTextReader) scope.launch { listState.animateScrollBy(-screenHeightPx * 0.9f) }
                            else screenModel.goToPrevPage()
                        }
                        x > width * 2 / 3 -> {
                            if (isTextReader) scope.launch { listState.animateScrollBy(screenHeightPx * 0.9f) }
                            else screenModel.goToNextPage()
                        }
                        else -> showControls = !showControls
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isTextReader) {
                        (allPages.firstOrNull() as? ReaderPage.Text)?.let { page ->
                            ReaderPageContent(page, screenModel, scaleMode, textColor = textColor, onTap = handleTap, listState = listState)
                        }
                    } else if (readingMode == ReadingMode.HORIZONTAL) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = false,
                            beyondViewportPageCount = 1,
                            key = { it }
                        ) { index ->
                            if (index < allPages.size) {
                                LaunchedEffect(index) { screenModel.loadPage(index) }
                                ReaderPageContent(allPages[index], screenModel, scaleMode, textColor = textColor, onTap = handleTap)
                            } else if (index == allPages.size && allPages.isNotEmpty()) {
                                ChapterTransitionPage(screenModel)
                            }
                        }
                        // Sync state from UI to Model
                        LaunchedEffect(pagerState.currentPage) {
                            if (pagerState.currentPage < pageCount) {
                                screenModel.setPage(pagerState.currentPage)
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(allPages) { index, page ->
                                LaunchedEffect(index) { screenModel.loadPage(index) }
                                ReaderPageContent(page, screenModel, scaleMode, true, textColor, onTap = handleTap)
                            }
                            item { ChapterTransitionPage(screenModel) }
                        }
                        LaunchedEffect(listState.firstVisibleItemIndex) { screenModel.setPage(listState.firstVisibleItemIndex) }
                    }
                }

                if (!showControls && !isTextReader) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    ) {
                        Text("${currentPage + 1} / $pageCount", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp)
                    }
                }

                AnimatedVisibility(visible = showControls, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    ReaderTopBar(chapterName, { navigator.pop() }, { scope.launch { drawerState.open() } }, { showSettingsSheet = true }, if (isTextReader) ({ showTtsPlayer = true }) else null)
                }

                AnimatedVisibility(visible = showControls, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Column(modifier = Modifier.fillMaxWidth().background(CardBackground.copy(alpha = 0.95f)).padding(bottom = 16.dp)) {
                    // Slider trang — ẩn với text reader (progress được tính theo đoạn văn)
                    if (!isTextReader) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Trước", color = TextSecondary, fontSize = 12.sp)
                            Slider(value = (currentPage + 1).toFloat(), onValueChange = { screenModel.setPage(it.toInt() - 1) }, valueRange = 1f..(pageCount.coerceAtLeast(1).toFloat()), modifier = Modifier.weight(1f).padding(horizontal = 8.dp), colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange))
                            Text("Tiếp", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = Color.White) }
                            IconButton(onClick = { screenModel.loadPrevChapter() }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                            IconButton(onClick = { screenModel.setOrientation((orientation + 1) % 3) }) { Icon(if (orientation == 1) Icons.Default.ScreenLockPortrait else if (orientation == 2) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenRotation, null, tint = Color.White) }
                            IconButton(onClick = { showTtsPlayer = true }) { Icon(Icons.Default.Headphones, null, tint = if (isTtsPlaying) PrimaryOrange else Color.White) }
                            IconButton(onClick = { screenModel.loadNextChapter() }) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                            IconButton(onClick = { showSettingsSheet = true }) { Icon(Icons.Default.Settings, null, tint = Color.White) }
                        }
                    }
                }

                if (brightness < 1.0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1.0f - brightness)))

                if (showControls) {
                    Surface(onClick = { screenModel.setReaderTheme(if (readerTheme == 0) 1 else 0) }, color = CardBackground.copy(alpha = 0.8f), shape = CircleShape, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(if (readerTheme == 0) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = PrimaryOrange) }
                    }
                }

                if (!showControls && !isLoading) ReaderSystemOverlay(Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }

            if (showSettingsSheet) ReaderSettingsSheet(onDismissRequest = { showSettingsSheet = false }, screenModel = screenModel, isTextReader = isTextReader)
            if (showTtsPlayer) Box(modifier = Modifier.align(Alignment.BottomCenter)) { TtsPlayerBar(screenModel = screenModel, onShowSettings = { showTtsSettings = true }, onClose = { showTtsPlayer = false }) }
            if (showTtsSettings) TtsSettingsDialog({ showTtsSettings = false }, screenModel)
        }
    }
}
