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
                "Mục lục",
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
    onTap: ((Float, Float, Float, Float) -> Unit)? = null,
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

    // Nếu cropBorders bật: decode bitmap và crop viền trắng
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
                cropped ?: model // Fallback về ảnh gốc nếu không crop được
            } catch (e: Exception) {
                model // Fallback
            }
        }
    }

    // Tính aspect ratio cho Webtoon mode để xếp ảnh khít nhau
    var webtoonAspectRatio by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(displayModel) {
        if (displayModel != null) {
            webtoonAspectRatio = withContext(Dispatchers.IO) {
                try {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val ratio = when (val m = displayModel) {
                        is String -> {
                            android.graphics.BitmapFactory.decodeFile(m, options)
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                options.outWidth.toFloat() / options.outHeight.toFloat()
                            } else null
                        }
                        is ByteArray -> {
                            android.graphics.BitmapFactory.decodeByteArray(m, 0, m.size, options)
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                options.outWidth.toFloat() / options.outHeight.toFloat()
                            } else null
                        }
                        is android.graphics.Bitmap -> m.width.toFloat() / m.height.toFloat()
                        else -> null
                    }
                    
                    // Auto-detect Webtoon based on aspect ratio if currently not in Webtoon mode
                    if (!isWebtoon && ratio != null && ratio < 0.5f) {
                        withContext(Dispatchers.Main) {
                            screenModel.setReadingModeTemp(ReadingMode.WEBTOON)
                        }
                    }
                    
                    ratio
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    // Wrap with Box that respects aspect ratio for Webtoon, else fillMax
    val containerModifier = if (isWebtoon) {
        if (webtoonAspectRatio != null) {
            Modifier.fillMaxWidth().aspectRatio(webtoonAspectRatio!!)
        } else {
            Modifier.fillMaxWidth().wrapContentHeight() // Fallback before ratio is calculated
        }
    } else {
        Modifier.fillMaxSize()
    }

    Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.fillMaxSize(),
                    scaleMode = if (isWebtoon) 1 else scaleMode,
                    isWebtoon = isWebtoon,
                    onTap = { x, y, w, h ->
                        currentOnTap.value?.invoke(x, y, w, h)
                    },
                    onLongPress = {
                        page?.let { currentOnLongPress.value?.invoke(it) }
                    }
                )
            }
            page is ReaderPage.Online && page.hasError -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BrokenImage, contentDescription = "Lỗi", tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Text("Tải ảnh thất bại", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    Button(
                        onClick = { screenModel.loadPage(page.index) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                    ) {
                        Text("Thử lại", color = Color.White)
                    }
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = PrimaryOrange, modifier = Modifier.size(32.dp))
                    if (page is ReaderPage.Archive) {
                        Text(page.entryName.substringAfterLast('/'), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                    } else if (page is ReaderPage.Online) {
                        Text("Đang chờ tải...", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
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
    onTap: ((Float, Float, Float, Float) -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null
) {
    val currentOnTap = rememberUpdatedState(onTap)
    val cropBorders by screenModel.cropBorders.collectAsState()
    if (page is ReaderPage.Text) {
        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { offset ->
                currentOnTap.value?.invoke(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
            }
        }) {
            // TextReaderContent has been replaced by TextReaderViewer and should not be used in Pager/Webtoon mode
            Text("Lỗi: Text reader content cannot be rendered here.", color = textColor)
        }
    } else {
        PageImage(page, screenModel, scaleMode, isWebtoon, cropBorders, onTap)
    }
}

@Composable
fun ChapterTransitionPage(screenModel: ReaderScreenModel, transition: ReaderPage.Transition? = null) {
    val chapters by screenModel.chapters.collectAsState()
    val currentChapterId = transition?.chapter?.id ?: screenModel.currentChapter?.id ?: 0L
    // Sort tăng dần theo số chương (giống Mihon dùng sortDescending=false)
    // Index+1 = chương có số lớn hơn = chương tiếp theo cần đọc
    val sortedAsc = remember(chapters) { chapters.sortedBy { it.chapterNumber } }
    val currentIndex = sortedAsc.indexOfFirst { it.id == currentChapterId }
    val nextChapter = transition?.nextChapter ?: (if (currentIndex >= 0) sortedAsc.getOrNull(currentIndex + 1) else null)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Đã hết chương hiện tại", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            if (nextChapter != null) {
                Text("Chương tiếp theo: ${nextChapter.name}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                // Because we have continuous scrolling, we don't strictly need the button anymore,
                // but we can keep it for users who want to jump to the start of the next chapter explicitly.
                Button(
                    onClick = { screenModel.navigateToChapter(nextChapter) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Tới chương tiếp", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Đây là chương cuối cùng", color = PrimaryOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    onBackClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTtsClick: (() -> Unit)? = null,
    isIncognito: Boolean = false
) {
    androidx.compose.material3.TopAppBar(
        title = { 
            Text(
                title, 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            ) 
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) 
            }
        },
        actions = {
            if (isIncognito) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Incognito", tint = Color.Gray, modifier = Modifier.padding(end = 8.dp))
            }
            IconButton(onClick = onTocClick) { Icon(Icons.Default.FormatListBulleted, null, tint = Color.White) }
            if (onTtsClick != null) {
                IconButton(onClick = onTtsClick) { Icon(Icons.Default.Headphones, null, tint = Color.White) }
            }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Tune, null, tint = Color.White) }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars
    )
}

@Composable
fun ReaderSystemOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var batteryPercent by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while(true) {
            // Fix: Dùng BatteryManager API thay vì registerReceiver để tránh SecurityException trên Android 13+
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            batteryPercent = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: run {
                // Fallback cho các thiết bị cũ không hỗ trợ getIntProperty
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

