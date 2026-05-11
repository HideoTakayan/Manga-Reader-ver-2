@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.manga_readerver2.features.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import logcat.logcat
import logcat.LogPriority
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.items

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

    var hasAutoShownTts by remember { mutableStateOf(false) }
    LaunchedEffect(isTextReader) {
        if (isTextReader && !hasAutoShownTts) {
            showTtsPlayer = true
            hasAutoShownTts = true
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(mangaId, chapterId) {
        screenModel.loadChapter(mangaId, chapterId)
    }

    // Sync ScreenModel -> UI (List only, Horizontal handled by AndroidView update)
    LaunchedEffect(currentPage, readingMode) {
        if (readingMode == ReadingMode.VERTICAL) {
            if (pageCount > 0 && listState.firstVisibleItemIndex != currentPage && currentPage < pageCount && !listState.isScrollInProgress) {
                listState.scrollToItem(currentPage)
            }
        }
    }

    // Sync UI (List) -> ScreenModel
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (readingMode == ReadingMode.VERTICAL && index < pageCount && index != currentPage) {
                screenModel.setPage(index)
            }
        }
    }

    DisposableEffect(keepScreenOn) {
        val activity = context as? Activity
        if (keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val handleTap: (Float, Float, Float) -> Unit = { x, y, width ->
        when {
            x < width / 3 -> {
                if (isTextReader) scope.launch { listState.animateScrollBy(-screenHeightPx * 0.9f) }
                else screenModel.goToPrevPage()
            }
            x > width * 2 / 3 -> {
                if (isTextReader) scope.launch { listState.animateScrollBy(screenHeightPx * 0.9f) }
                else screenModel.goToNextPage()
            }
            else -> { showControls = !showControls }
        }
    }

    Scaffold(
        containerColor = bgColor,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(color = PrimaryOrange, modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                // Content Layer
                if (isTextReader) {
                    (allPages.firstOrNull() as? ReaderPage.Text)?.let { page ->
                        ReaderPageContent(page, screenModel, scaleMode, textColor = textColor, onTap = handleTap, listState = listState)
                    }
                } else if (readingMode == ReadingMode.HORIZONTAL) {
                    if (currentPage < allPages.size) {
                        LaunchedEffect(currentPage) {
                            screenModel.loadPage(currentPage)
                        }
                        ReaderPageContent(
                            page = allPages[currentPage],
                            screenModel = screenModel,
                            scaleMode = scaleMode,
                            textColor = textColor,
                            onTap = handleTap
                        )
                    } else {
                        ChapterTransitionPage(screenModel)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = allPages,
                            key = { index, page -> "page_${chapterId}_${index}" }
                        ) { index, page ->
                            LaunchedEffect(index) { screenModel.loadPage(index) }
                            ReaderPageContent(
                                page = page,
                                screenModel = screenModel,
                                scaleMode = scaleMode,
                                isWebtoon = true,
                                textColor = textColor,
                                onTap = handleTap
                            )
                        }
                        item(key = "transition_${chapterId}") { ChapterTransitionPage(screenModel) }
                    }
                }

                // Overlay: Page Indicator
                if (!showControls && !isTextReader) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    ) {
                        Text("${currentPage + 1} / $pageCount", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp)
                    }
                }

                // Overlay: Top Bar
                AnimatedVisibility(visible = showControls, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }, modifier = Modifier.align(Alignment.TopCenter)) {
                    Box(modifier = Modifier.clickable(onClick = {}, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })) {
                        ReaderTopBar(chapterName, { navigator.pop() }, { scope.launch { drawerState.open() } }, { showSettingsSheet = true }, if (isTextReader) ({ showTtsPlayer = true }) else null)
                    }
                }

                // Overlay: Bottom Bar
                AnimatedVisibility(visible = showControls, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Column(modifier = Modifier.fillMaxWidth().background(CardBackground.copy(alpha = 0.95f)).clickable(onClick = {}, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }).padding(bottom = 16.dp)) {
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

                // Overlay: Theme Toggle
                if (showControls) {
                    Surface(onClick = { screenModel.setReaderTheme(if (readerTheme == 0) 1 else 0) }, color = CardBackground.copy(alpha = 0.8f), shape = CircleShape, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(if (readerTheme == 0) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = PrimaryOrange) }
                    }
                }

                // Overlay: System Stats
                if (!showControls && !isLoading) ReaderSystemOverlay(Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }

            // Global Overlays (Outside main content check)
            if (brightness < 1.0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1.0f - brightness)))
            if (showSettingsSheet) ReaderSettingsSheet(onDismissRequest = { showSettingsSheet = false }, screenModel = screenModel, isTextReader = isTextReader)
            if (showTtsPlayer) Box(modifier = Modifier.align(Alignment.BottomCenter)) { TtsPlayerBar(screenModel = screenModel, onShowSettings = { showTtsSettings = true }, onClose = { showTtsPlayer = false }) }
            if (showTtsSettings) TtsSettingsDialog({ showTtsSettings = false }, screenModel)
        }
    }
}
