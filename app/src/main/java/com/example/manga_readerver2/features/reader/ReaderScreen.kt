@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.manga_readerver2.features.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val incognitoMode by screenModel.incognitoMode.collectAsState()
    val chapterName by screenModel.chapterName.collectAsState()
    val scaleMode by screenModel.scaleMode.collectAsState()
    
    val orientation by screenModel.orientation.collectAsState()
    val readerTheme by screenModel.readerTheme.collectAsState()
    val isTtsPlaying by screenModel.isTtsPlaying.collectAsState()
    val keepScreenOn by screenModel.keepScreenOn.collectAsState()
    val brightness by screenModel.brightness.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current

    LaunchedEffect(Unit) {
        // Initialization handled by loadChapter below
    }

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
    // Cờ trạng thái để kiểm soát sự kiện chạm màn hình (tap) khi người dùng đang tương tác với thanh trượt (slider)
    var isSeeking by remember { mutableStateOf(false) }

    val window = activity?.window
    LaunchedEffect(showControls) {
        if (window != null) {
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (showControls) {
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var hasAutoShownTts by remember { mutableStateOf(false) }
    LaunchedEffect(isTextReader) {
        if (isTextReader && !hasAutoShownTts) {
            showTtsPlayer = true
            hasAutoShownTts = true
        }
    }
    val autoScrollSpeed by screenModel.autoScrollSpeed.collectAsState()
    var isAutoScrolling by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val webtoonSidePadding by screenModel.webtoonSidePadding.collectAsState()
    var webtoonScale by remember { mutableFloatStateOf(1f) }
    var webtoonOffset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        webtoonScale = (webtoonScale * zoomChange).coerceIn(1f, 3f)
        if (webtoonScale == 1f) {
            webtoonOffset = Offset.Zero
        } else {
            val maxX = (screenWidthPx * (webtoonScale - 1)) / 2
            val maxY = (screenHeightPx * (webtoonScale - 1)) / 2
            val newX = (webtoonOffset.x + offsetChange.x * webtoonScale).coerceIn(-maxX, maxX)
            val newY = (webtoonOffset.y + offsetChange.y * webtoonScale).coerceIn(-maxY, maxY)
            webtoonOffset = Offset(newX, newY)
        }
    }

    LaunchedEffect(isAutoScrolling, autoScrollSpeed, readingMode, isTextReader) {
        if (isAutoScrolling && (readingMode == ReadingMode.WEBTOON || isTextReader)) {
            while (isActive) {
                if (listState.canScrollForward) {
                    listState.scrollBy(autoScrollSpeed * density.density)
                    delay(16L)
                } else {
                    // Reached the bottom, load next chapter
                    screenModel.loadNextChapter()
                    // Wait a bit before continuing to scroll so the new chapter can load
                    delay(1000L) 
                }
            }
        }
    }

    // Horizontal swipe tracking
    var horizontalDragTotal by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 80f // px để tính là swipe chuyển trang

    LaunchedEffect(mangaId, chapterId) {
        screenModel.loadChapter(mangaId, chapterId)
    }

    // Sync ScreenModel -> UI (List only)
    LaunchedEffect(currentPage, readingMode) {
        if (readingMode == ReadingMode.WEBTOON) {
            if (pageCount > 0 && listState.firstVisibleItemIndex != currentPage && currentPage < pageCount && !listState.isScrollInProgress) {
                listState.scrollToItem(currentPage)
            }
        }
    }

    // Sync UI (List) -> ScreenModel
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (readingMode == ReadingMode.WEBTOON && index < pageCount && index != currentPage) {
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

    // handleTap: check custom 3x3 tap zones
    val customTapZones by screenModel.customTapZones.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val handleTap: (Float, Float, Float, Float) -> Unit = { x, y, width, height ->
        if (isAutoScrolling) {
            isAutoScrolling = false
        } else if (isSeeking) {
            // Tạm thời vô hiệu hóa thao tác chạm để tránh nhảy trang ngoài ý muốn sau khi thao tác trên thanh trượt
            // do hệ thống có thể nhận diện sai thao tác thả tay (pointer up) thành một thao tác chạm (tap)
            isSeeking = false
        } else {
            val col = (x / (width / 3)).toInt().coerceIn(0, 2)
            val row = (y / (height / 3)).toInt().coerceIn(0, 2)
            val index = row * 3 + col
            val action = customTapZones.getOrNull(index) ?: com.example.manga_readerver2.core.preference.ReaderPreferences.TapAction.NONE

            when (action) {
                com.example.manga_readerver2.core.preference.ReaderPreferences.TapAction.NEXT -> {
                    if (readingMode == ReadingMode.WEBTOON || readingMode == ReadingMode.VERTICAL) {
                        coroutineScope.launch { listState.animateScrollBy(height * 0.8f) }
                    } else {
                        screenModel.goToNextPage()
                    }
                }
                com.example.manga_readerver2.core.preference.ReaderPreferences.TapAction.PREVIOUS -> {
                    if (readingMode == ReadingMode.WEBTOON || readingMode == ReadingMode.VERTICAL) {
                        coroutineScope.launch { listState.animateScrollBy(-height * 0.8f) }
                    } else {
                        screenModel.goToPrevPage()
                    }
                }
                com.example.manga_readerver2.core.preference.ReaderPreferences.TapAction.MENU -> {
                    showControls = !showControls
                }
                com.example.manga_readerver2.core.preference.ReaderPreferences.TapAction.NONE -> {
                    // Do nothing
                }
            }
        }
    }
    
    var longPressedPage by remember { mutableStateOf<ReaderPage?>(null) }
    val handleLongPress: (ReaderPage) -> Unit = { page ->
        longPressedPage = page
    }

    Scaffold(
        containerColor = bgColor,
        modifier = Modifier.fillMaxSize()
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(color = PrimaryOrange, modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                // Content Layer
                if (isTextReader) {
                    val textBlocks by screenModel.textBlocks.collectAsState()
                    com.example.manga_readerver2.features.reader.viewer.TextReaderViewer(
                        blocks = textBlocks,
                        screenModel = screenModel,
                        listState = listState,
                        textColor = textColor,
                        onTap = handleTap
                    )
                } else if (readingMode == ReadingMode.RIGHT_TO_LEFT || readingMode == ReadingMode.LEFT_TO_RIGHT || readingMode == ReadingMode.VERTICAL) {
                    val cropBorders by screenModel.cropBorders.collectAsState()
                    com.example.manga_readerver2.features.reader.viewer.PagerViewer(
                        pages = allPages,
                        screenModel = screenModel,
                        readingMode = readingMode,
                        scaleMode = scaleMode,
                        cropBorders = cropBorders,
                        bgColor = bgColor,
                        isAutoScrolling = isAutoScrolling,
                        autoScrollSpeed = autoScrollSpeed,
                        onTap = handleTap,
                        onLongPress = handleLongPress
                    )
                } else {
                    val sidePaddingDp = (screenWidthPx / density.density * (webtoonSidePadding / 100f)).dp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(state = transformableState)
                            .pointerInput(webtoonScale) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        if (webtoonScale > 1f) {
                                            webtoonScale = 1f
                                            webtoonOffset = Offset.Zero
                                        } else {
                                            webtoonScale = 2f
                                            val maxX = (screenWidthPx * (webtoonScale - 1)) / 2
                                            val maxY = (screenHeightPx * (webtoonScale - 1)) / 2
                                            // Center the tapped point
                                            val newX = (screenWidthPx / 2 - offset.x) * webtoonScale
                                            val newY = (screenHeightPx / 2 - offset.y) * webtoonScale
                                            webtoonOffset = Offset(newX.coerceIn(-maxX, maxX), newY.coerceIn(-maxY, maxY))
                                        }
                                    }
                                )
                            }
                            .pointerInput(webtoonScale) {
                                if (webtoonScale > 1f) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        val maxX = (screenWidthPx * (webtoonScale - 1)) / 2
                                        val newX = (webtoonOffset.x + dragAmount).coerceIn(-maxX, maxX)
                                        webtoonOffset = Offset(newX, webtoonOffset.y)
                                    }
                                }
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = webtoonScale,
                                    scaleY = webtoonScale,
                                    translationX = webtoonOffset.x,
                                    translationY = webtoonOffset.y
                                )
                                .padding(horizontal = sidePaddingDp),
                            verticalArrangement = Arrangement.Top
                        ) {
                            itemsIndexed(
                                items = allPages,
                                key = { index, page -> "page_${chapterId}_${index}" }
                            ) { index, page ->
                                LaunchedEffect(index) { 
                                    screenModel.loadPage(index) 
                                    if (index + 1 < allPages.size) screenModel.loadPage(index + 1)
                                    if (index + 2 < allPages.size) screenModel.loadPage(index + 2)
                                }
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
                        ReaderTopBar(chapterName, { navigator.pop() }, { scope.launch { drawerState.open() } }, { showSettingsSheet = true }, if (isTextReader) ({ showTtsPlayer = true }) else null, incognitoMode)
                    }
                }

                // Overlay: Bottom Bar
                AnimatedVisibility(visible = showControls, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Column(modifier = Modifier.fillMaxWidth().background(CardBackground.copy(alpha = 0.95f)).clickable(onClick = {}, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }).navigationBarsPadding().padding(bottom = 16.dp)) {
                        if (!isTextReader) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Trước", color = TextSecondary, fontSize = 12.sp)
                                Slider(
                                    value = (currentPage + 1).toFloat(),
                                    onValueChange = {
                                        // Bật cờ trạng thái để chặn các sự kiện chạm khác khi người dùng đang kéo thanh trượt
                                        isSeeking = true
                                        screenModel.setPage(it.toInt() - 1)
                                    },
                                    onValueChangeFinished = {
                                        // Duy trì trạng thái khóa thêm một khoảng thời gian ngắn sau khi nhả tay,
                                        // nhằm ngăn chặn xung đột giữa thao tác thả tay và thao tác chạm màn hình.
                                        coroutineScope.launch {
                                            delay(200)
                                            isSeeking = false
                                        }
                                    },
                                    valueRange = 1f..(pageCount.coerceAtLeast(1).toFloat()),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                                )
                                Text("Tiếp", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val isBookmarked by screenModel.isChapterBookmarked.collectAsState()
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = Color.White) }
                            IconButton(onClick = { screenModel.loadPrevChapter() }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                            IconButton(onClick = { screenModel.toggleChapterBookmark() }) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark chương",
                                    tint = if (isBookmarked) PrimaryOrange else Color.White
                                )
                            }
                            IconButton(onClick = { screenModel.setOrientation((orientation + 1) % 3) }) { Icon(if (orientation == 1) Icons.Default.ScreenLockPortrait else if (orientation == 2) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenRotation, null, tint = Color.White) }
                            if (!isTextReader) {
                                IconButton(onClick = { isAutoScrolling = true; showControls = false }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Tự động cuộn/lật", tint = Color.White)
                                }
                            }
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
                if (!showControls && !isLoading) ReaderSystemOverlay(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(8.dp))

                // Overlay: Auto Scroll Panel
                if (isAutoScrolling) {
                    Surface(
                        color = CardBackground.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            IconButton(onClick = { screenModel.setAutoScrollSpeed((autoScrollSpeed - 0.5f).coerceAtLeast(0.5f)) }) {
                                Icon(Icons.Default.Remove, null, tint = Color.White)
                            }
                            Text("${String.format(Locale.US, "%.1f", autoScrollSpeed)}x", color = PrimaryOrange, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            IconButton(onClick = { screenModel.setAutoScrollSpeed((autoScrollSpeed + 0.5f).coerceAtMost(5.0f)) }) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { isAutoScrolling = false }) {
                                Icon(Icons.Default.Pause, null, tint = PrimaryOrange)
                            }
                        }
                    }
                }
            }

            // Global Overlays (Outside main content check)
            val customColorFilter by screenModel.customColorFilter.collectAsState()
            val customColorFilterColor by screenModel.customColorFilterColor.collectAsState()
            val customColorFilterAlpha by screenModel.customColorFilterAlpha.collectAsState()
            val customColorFilterBlendMode by screenModel.customColorFilterBlendMode.collectAsState()

            if (customColorFilter) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val blendMode = when (customColorFilterBlendMode) {
                        0 -> androidx.compose.ui.graphics.BlendMode.Multiply
                        1 -> androidx.compose.ui.graphics.BlendMode.Screen
                        2 -> androidx.compose.ui.graphics.BlendMode.Overlay
                        else -> androidx.compose.ui.graphics.BlendMode.Multiply
                    }
                    drawRect(
                        color = Color(customColorFilterColor).copy(alpha = customColorFilterAlpha),
                        blendMode = blendMode
                    )
                }
            }
            if (brightness < 1.0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1.0f - brightness)))
            if (showSettingsSheet) ReaderSettingsSheet(onDismissRequest = { showSettingsSheet = false }, screenModel = screenModel, isTextReader = isTextReader)
            if (showTtsPlayer) Box(modifier = Modifier.align(Alignment.BottomCenter)) { TtsPlayerBar(screenModel = screenModel, onShowSettings = { showTtsSettings = true }, onClose = { showTtsPlayer = false }) }
            if (showTtsSettings) TtsSettingsDialog({ showTtsSettings = false }, screenModel)
            
            if (longPressedPage != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { longPressedPage = null },
                    title = { Text("Tùy chọn trang", color = Color.White) },
                    containerColor = CardBackground,
                    text = {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Column {
                            androidx.compose.material3.TextButton(onClick = {
                                val url = when (val p = longPressedPage) {
                                    is com.example.manga_readerver2.features.reader.ReaderPage.Online -> p.url
                                    is com.example.manga_readerver2.features.reader.ReaderPage.Local -> p.file.absolutePath
                                    else -> ""
                                }
                                if (url.startsWith("http")) {
                                    try {
                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                                            .setTitle("Đang tải ảnh...")
                                            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "manga_image_${System.currentTimeMillis()}.jpg")
                                        val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        manager.enqueue(request)
                                        android.widget.Toast.makeText(context, "Đã bắt đầu tải ảnh", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Không thể tải ảnh", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Ảnh cục bộ, không hỗ trợ tải lại", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                longPressedPage = null
                            }) {
                                Text("Tải trang này", color = PrimaryOrange)
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                val url = when (val p = longPressedPage) {
                                    is com.example.manga_readerver2.features.reader.ReaderPage.Online -> p.url
                                    is com.example.manga_readerver2.features.reader.ReaderPage.Local -> p.file.absolutePath
                                    else -> ""
                                }
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ ảnh"))
                                longPressedPage = null
                            }) {
                                Text("Chia sẻ URL ảnh", color = PrimaryOrange)
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { longPressedPage = null }) { Text("Đóng", color = TextSecondary) }
                    }
                )
            }
        }
    }
}

