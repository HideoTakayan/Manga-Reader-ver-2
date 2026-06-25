@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.manga_readerver2.features.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import com.example.manga_readerver2.ui.theme.CardBackground
import com.example.manga_readerver2.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReaderDrawer(
    chapters: List<Chapter>,
    currentChapterId: Long,
    onChapterClick: (Chapter) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(320.dp),
        color = Color(0xFF1A1A1A)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Má»¥c lá»¥c",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(chapters) { _, chapter ->
                    val isSelected = chapter.id == currentChapterId
                    Surface(
                        onClick = { onChapterClick(chapter) },
                        color = if (isSelected) PrimaryOrange.copy(alpha = 0.1f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Box(modifier = Modifier.size(6.dp).background(PrimaryOrange, CircleShape))
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                chapter.name,
                                color = if (isSelected) PrimaryOrange else Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PageImage(
    page: ReaderPage?,
    screenModel: ReaderScreenModel,
    scaleMode: Int = 0,
    isWebtoon: Boolean = false,
    cropBorders: Boolean = false,
    onTap: ((Float, Float, Float) -> Unit)? = null,
    onLongPress: ((ReaderPage) -> Unit)? = null
) {
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnLongPress = rememberUpdatedState(onLongPress)
    val context = LocalContext.current
    
    val invertColors by screenModel.invertColors.collectAsState()
    val grayscale by screenModel.grayscale.collectAsState()

    // Produce raw model (file path / ByteArray / Bitmap)
    var model by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(page) {
        model = withContext(Dispatchers.IO) {
            when (page) {
                is ReaderPage.Local   -> page.file.absolutePath
                is ReaderPage.Archive -> screenModel.getPageBytes(page)
                is ReaderPage.Pdf     -> screenModel.renderPdfPage(page)
                is ReaderPage.Online  -> page.localFile ?: page.url.takeIf { it.isNotEmpty() }
                else -> null
            }
        }
    }

    // Náº¿u cropBorders báº­t: decode bitmap vĂ  crop viá»n tráº¯ng
    var displayModel by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(model, cropBorders) {
        if (!cropBorders || model == null) {
            displayModel = model
            return@LaunchedEffect
        }
        displayModel = withContext(Dispatchers.IO) {
            try {
                val cropped = when (val m = model) {
                    is String -> com.example.manga_readerver2.core.utils.ImageBorderCropper
                        .cropBordersFromFile(java.io.File(m))
                    is ByteArray -> com.example.manga_readerver2.core.utils.ImageBorderCropper
                        .cropBorders(m)
                    else -> null
                }
                cropped ?: model // Fallback vá» áº£nh gá»‘c náº¿u khĂ´ng crop Ä‘Æ°á»£c
            } catch (e: Exception) {
                model // Fallback
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            page is ReaderPage.Online && page.isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = PrimaryOrange, modifier = Modifier.size(32.dp))
                    Text("Trang ${page.index + 1}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            displayModel != null -> {
                com.example.manga_readerver2.features.reader.components.SubsamplingImage(
                    displayModel!!,
                    modifier = if (isWebtoon) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize(),
                    scaleMode = if (isWebtoon) 1 else scaleMode,
                    isWebtoon = isWebtoon,
                    onTap = { x, y, w ->
                        currentOnTap.value?.invoke(x, y, w)
                    },
                    onLongPress = {
                        page?.let { currentOnLongPress.value?.invoke(it) }
                    }
                )
            }
            page is ReaderPage.Online && page.hasError -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BrokenImage, contentDescription = "Lá»—i", tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Text("Táº£i áº£nh tháº¥t báº¡i", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    Button(
                        onClick = { screenModel.loadPage(page.index) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                    ) {
                        Text("Thá»­ láº¡i", color = Color.White)
                    }
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = PrimaryOrange, modifier = Modifier.size(32.dp))
                    if (page is ReaderPage.Archive) {
                        Text(page.entryName.substringAfterLast('/'), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                    } else if (page is ReaderPage.Online) {
                        Text("Äang chá» táº£i...", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}


@Composable
fun ReaderPageContent(
    page: ReaderPage?,
    screenModel: ReaderScreenModel,
    scaleMode: Int = 0,
    isWebtoon: Boolean = false,
    textColor: Color = Color.White,
    onTap: ((Float, Float, Float) -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null
) {
    val currentOnTap = rememberUpdatedState(onTap)
    val cropBorders by screenModel.cropBorders.collectAsState()
    if (page is ReaderPage.Text) {
        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { offset ->
                currentOnTap.value?.invoke(offset.x, offset.y, size.width.toFloat())
            }
        }) {
            TextReaderContent(page.content, listState = listState, screenModel = screenModel, textColor = textColor)
        }
    } else {
        PageImage(page, screenModel, scaleMode, isWebtoon, cropBorders, onTap)
    }
}

@Composable
fun ChapterTransitionPage(screenModel: ReaderScreenModel) {
    val chapters by screenModel.chapters.collectAsState()
    val currentChapterId = screenModel.currentChapter?.id ?: 0L
    // Sort tÄƒng dáº§n theo sá»‘ chÆ°Æ¡ng â€” giá»‘ng Mihon dĂ¹ng sortDescending=false
    // Index+1 = chÆ°Æ¡ng cĂ³ sá»‘ lá»›n hÆ¡n = chÆ°Æ¡ng tiáº¿p theo cáº§n Ä‘á»c
    val sortedAsc = remember(chapters) { chapters.sortedBy { it.chapterNumber } }
    val currentIndex = sortedAsc.indexOfFirst { it.id == currentChapterId }
    val nextChapter = if (currentIndex >= 0) sortedAsc.getOrNull(currentIndex + 1) else null

    LaunchedEffect(nextChapter) {
        if (nextChapter != null) {
            delay(1000) // Äá»£i 1 giĂ¢y Ä‘á»ƒ ngÆ°á»i dĂ¹ng tháº¥y thĂ´ng bĂ¡o
            screenModel.navigateToChapter(nextChapter)
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ÄĂ£ háº¿t chÆ°Æ¡ng hiá»‡n táº¡i", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            if (nextChapter != null) {
                Text("Äang táº£i: ${nextChapter.name}...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(color = PrimaryOrange)
            } else {
                Text("ÄĂ¢y lĂ  chÆ°Æ¡ng cuá»‘i cĂ¹ng", color = PrimaryOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TextReaderContent(
    content: String,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    screenModel: ReaderScreenModel? = null,
    textColor: Color = Color.White
) {
    val paragraphs = remember(content) { content.split("\n").filter { it.isNotBlank() } }
    val currentTtsParagraph by (screenModel?.currentTtsParagraph?.collectAsState() ?: mutableStateOf(-1))
    
    LaunchedEffect(paragraphs.size) {
        screenModel?.setTextPageCount(paragraphs.size)
    }

    // Auto-scroll to active TTS paragraph
    LaunchedEffect(currentTtsParagraph) {
        if (currentTtsParagraph >= 0 && listState != null) {
            // Chá»‰ cuá»™n náº¿u paragraph Ä‘ang Ä‘á»c khĂ´ng náº±m trong view hiá»‡n táº¡i
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isVisible = visibleItems.any { it.index == currentTtsParagraph }
            if (!isVisible) {
                listState.animateScrollToItem(currentTtsParagraph)
            }
        }
    }
    
    val fontSize = screenModel?.fontSize?.collectAsState()?.value ?: 18f
    val lineSpacing = screenModel?.lineSpacing?.collectAsState()?.value ?: 1.2f
    val modifier = if (listState != null) Modifier.fillMaxSize().padding(horizontal = 20.dp) else Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
    
    if (listState != null) {
        LazyColumn(
            state = listState, 
            modifier = modifier, 
            contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            itemsIndexed(paragraphs) { index, paragraph ->
                val isActive = index == currentTtsParagraph
                val backgroundColor = if (isActive) PrimaryOrange.copy(alpha = 0.15f) else Color.Transparent
                
                Surface(
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        paragraph, 
                        color = if (isActive) PrimaryOrange else textColor.copy(alpha = 0.9f), 
                        fontSize = fontSize.sp, 
                        lineHeight = fontSize.sp * lineSpacing, 
                        textAlign = TextAlign.Justify, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
            if (screenModel != null) {
                item {
                    ChapterTransitionPage(screenModel)
                }
            }
        }
    } else {
        Column(modifier = modifier) {
            paragraphs.forEachIndexed { index, paragraph ->
                val isActive = index == currentTtsParagraph
                Text(
                    paragraph, 
                    color = if (isActive) PrimaryOrange else textColor.copy(alpha = 0.9f), 
                    fontSize = fontSize.sp, 
                    lineHeight = fontSize.sp * lineSpacing, 
                    textAlign = TextAlign.Justify, 
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = (fontSize * (lineSpacing - 1f) / 2).dp)
                        .background(if (isActive) PrimaryOrange.copy(alpha = 0.1f) else Color.Transparent)
                )
            }
            if (screenModel != null) {
                ChapterTransitionPage(screenModel)
            }
        }
    }
}

@Composable
fun ReaderTopBar(
    title: String,
    onBackClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTtsClick: (() -> Unit)? = null,
    isIncognito: Boolean = false
) {
    Surface(color = Color.Black.copy(alpha = 0.8f), modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(title, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isIncognito) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Incognito", tint = Color.Gray, modifier = Modifier.padding(end = 8.dp))
            }
            IconButton(onClick = onTocClick) { Icon(Icons.Default.FormatListBulleted, null, tint = Color.White) }
            onTtsClick?.let { IconButton(onClick = it) { Icon(Icons.Default.Headphones, null, tint = Color.White) } }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Tune, null, tint = Color.White) }
        }
    }
}

@Composable
fun ReaderSystemOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var batteryPercent by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while(true) {
            // Fix: DĂ¹ng BatteryManager API thay vĂ¬ registerReceiver Ä‘á»ƒ trĂ¡nh SecurityException trĂªn Android 13+
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            batteryPercent = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: run {
                // Fallback cho cĂ¡c thiáº¿t bá»‹ cÅ© khĂ´ng há»— trá»£ getIntProperty
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                intent?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } ?: 0
            }
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }
    Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BatteryStd, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("$batteryPercent%  $currentTime", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

