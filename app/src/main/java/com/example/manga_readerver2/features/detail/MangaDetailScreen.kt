@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.example.manga_readerver2.features.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.example.manga_readerver2.core.navigation.LocalNavAnimatedVisibilityScope
import com.example.manga_readerver2.core.navigation.LocalSharedTransitionScope
import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.model.Category
import com.example.manga_readerver2.features.downloads.DownloadQueueScreen
import com.example.manga_readerver2.features.reader.ReaderScreen
import com.example.manga_readerver2.ui.theme.*
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.coroutines.launch

data class MangaDetailScreen(val mangaId: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { MangaDetailScreenModel() }
        val mangaState by screenModel.manga.collectAsState()
        val chapters by screenModel.chapters.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val isLiked by screenModel.isLiked.collectAsState()
        val sortMode by screenModel.sortMode.collectAsState()
        val filterMode by screenModel.filterMode.collectAsState()
        val source by screenModel.source.collectAsState()
        val errorMessage by screenModel.errorMessage.collectAsState()

        val listState = rememberLazyListState()
        val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }
        
        var showChapterDialog by remember { mutableStateOf(false) }
        var showCategoryDialog by remember { mutableStateOf(false) }
        var showCoverDialog by remember { mutableStateOf(false) }
        var selectedCategoryIds by remember { mutableStateOf(emptyList<Long>()) }

        val manga = mangaState

        LaunchedEffect(showCategoryDialog) {
            if (showCategoryDialog) {
                selectedCategoryIds = screenModel.getMangaCategoryIds()
            }
        }

        LaunchedEffect(mangaId) {
            screenModel.loadMangaDetail(mangaId)
        }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                MangaDetailTopBar(
                    title = if (isScrolled) manga?.title ?: "" else "",
                    onBack = { navigator.pop() },
                    onDownloadQueue = { navigator.push(DownloadQueueScreen()) },
                    onRefresh = { screenModel.refreshManual() },
                // isHttpSource: chĂ¡Â»â€° true khi lÄ‚Â  HTTP source thĂ¡ÂºÂ­t sĂ¡Â»Â± (APK extension)
                // VBook JS source cĂ…Â©ng cÄ‚Â³ source ID != 0 nÄ‚Âªn khÄ‚Â´ng dÄ‚Â¹ng `source != 0L`
                isHttpSource = source is eu.kanade.tachiyomi.source.online.HttpSource,
                isScrolled = isScrolled
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading && manga == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryOrange)
                        }
                    }
                    errorMessage != null && manga == null -> {
                        // HiĂ¡Â»â€¡n lĂ¡Â»â€”i rÄ‚Âµ rÄ‚Â ng thay vÄ‚Â¬ blank screen
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = errorMessage ?: "LĂ¡Â»â€”i khÄ‚Â´ng xÄ‚Â¡c Ă„â€˜Ă¡Â»â€¹nh",
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { navigator.pop() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                                ) { Text("Quay lĂ¡ÂºÂ¡i") }
                            }
                        }
                    }
                    manga != null -> {
                    MangaDetailContent(
                        paddingValues = paddingValues,
                        listState = listState,
                        manga = manga,
                        sourceName = source?.name ?: "Unknown",
                        chapters = chapters,
                        isLiked = isLiked,
                        onFavoriteClick = {
                            if (isLiked) {
                                // Ă„Âang liked Ă¢â€ â€™ mĂ¡Â»Å¸ dialog Ă„â€˜Ă¡Â»Æ’ Ă„â€˜Ă¡Â»â€¢i category hoĂ¡ÂºÂ·c unfavorite
                                showCategoryDialog = true
                            } else {
                                // ChĂ†Â°a liked Ă¢â€ â€™ toggle favorite rĂ¡Â»â€œi mĂ¡Â»Å¸ dialog chĂ¡Â»Ân category (chuĂ¡ÂºÂ©n Mihon)
                                screenModel.toggleLike()
                                showCategoryDialog = true
                            }
                        },
                        onChapterClick = { chapter ->
                            navigator.push(ReaderScreen(manga.id, chapter.id))
                        },
                        onDownloadChapter = { screenModel.downloadChapter(it) },
                        onFilterClick = { showChapterDialog = true },
                        onCoverClick = { showCoverDialog = true },
                        onWebViewClick = {
                            val httpSource = source as? HttpSource
                            val mangaUrl = if (httpSource != null) "${httpSource.baseUrl}${manga.url}" else null
                            if (mangaUrl != null) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mangaUrl))
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "NguĂ¡Â»â€œn khÄ‚Â´ng hĂ¡Â»â€” trĂ¡Â»Â£ xem trÄ‚Âªn trÄ‚Â¬nh duyĂ¡Â»â€¡t", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShareClick = {
                            val httpSource = source as? HttpSource
                            val mangaUrl = if (httpSource != null) "${httpSource.baseUrl}${manga.url}" else null
                            if (mangaUrl != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${manga.title}\n$mangaUrl")
                                }
                                context.startActivity(Intent.createChooser(intent, "Chia sĂ¡ÂºÂ» truyĂ¡Â»â€¡n"))
                            }
                        },
                        onTrackClick = {
                            Toast.makeText(context, "ChĂ¡Â»Â©c nĂ„Æ’ng theo dÄ‚Âµi sĂ¡ÂºÂ½ sĂ¡Â»â€ºm ra mĂ¡ÂºÂ¯t!", Toast.LENGTH_SHORT).show()
                        },
                        onTagClick = { tag ->
                            // TODO: ChuyĂ¡Â»Æ’n Ă„â€˜Ă¡ÂºÂ¿n mÄ‚Â n hÄ‚Â¬nh tÄ‚Â¬m kiĂ¡ÂºÂ¿m vĂ¡Â»â€ºi tag
                            Toast.makeText(context, "TÄ‚Â¬m kiĂ¡ÂºÂ¿m tag: $tag", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Resume Reading FAB
                    if (chapters.isNotEmpty()) {
                        val nextToRead = chapters.findLast { !it.read } ?: chapters.firstOrNull()
                        if (nextToRead != null) {
                            ExtendedFloatingActionButton(
                                onClick = { navigator.push(ReaderScreen(manga.id, nextToRead.id)) },
                                containerColor = PrimaryOrange,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .padding(bottom = 16.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (nextToRead.read) "BĂ¡ÂºÂ¯t Ă„â€˜Ă¡ÂºÂ§u lĂ¡ÂºÂ¡i" else "Ă„ÂĂ¡Â»Âc tiĂ¡ÂºÂ¿p", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                } // end when

                // Dialogs
                if (showChapterDialog) {
                    ChapterSettingsDialog(
                        currentSort = sortMode,
                        currentFilter = filterMode,
                        onDismiss = { showChapterDialog = false },
                        onSortChange = { screenModel.setSortMode(it) },
                        onFilterChange = { screenModel.setFilterMode(it) }
                    )
                }
                
                if (showCategoryDialog) {
                    val allCategories by screenModel.categories.collectAsState()
                    CategorySelectionDialog(
                        allCategories = allCategories,
                        selectedIds = selectedCategoryIds,
                        onDismiss = { showCategoryDialog = false },
                        onConfirm = { ids ->
                            screenModel.updateMangaCategories(ids)
                            showCategoryDialog = false
                        },
                        onUnfavorite = {
                            screenModel.toggleLike()
                            showCategoryDialog = false
                        }
                    )
                }

                if (showCoverDialog && manga != null) {
                    MangaCoverDialog(
                        manga = manga,
                        onDismiss = { showCoverDialog = false }
                    )
                }
            }
        }
    }
    
    private fun Manga.toSManga() = eu.kanade.tachiyomi.source.model.SManga.create().apply {
        url = this@toSManga.url
        title = this@toSManga.title
        thumbnail_url = this@toSManga.thumbnailUrl
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = this@toSManga.description
        genre = this@toSManga.genre?.joinToString(", ")
        status = this@toSManga.status.toInt()
    }
}

@Composable
fun MangaDetailTopBar(
    title: String,
    onBack: () -> Unit,
    onDownloadQueue: () -> Unit,
    onRefresh: () -> Unit,
    isHttpSource: Boolean,
    isScrolled: Boolean
) {
    val backgroundColor = if (isScrolled) Color(0xFF1E1E1E) else Color.Transparent
    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 18.sp
            )
            IconButton(onClick = onDownloadQueue) {
                Icon(Icons.Default.Download, "Download Queue", tint = Color.White)
            }
            if (isHttpSource) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun MangaDetailContent(
    paddingValues: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    manga: Manga,
    sourceName: String,
    chapters: List<Chapter>,
    isLiked: Boolean,
    onFavoriteClick: () -> Unit,
    onChapterClick: (Chapter) -> Unit,
    onDownloadChapter: (Chapter) -> Unit,
    onFilterClick: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClick: () -> Unit,
    onShareClick: () -> Unit,
    onTrackClick: () -> Unit,
    onTagClick: (String) -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                AsyncImage(
                    model = manga.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(80.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BackgroundDark.copy(alpha = 0.5f),
                                    BackgroundDark
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .height(175.dp)
                            .clickable { onCoverClick() },
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                AsyncImage(
                                    model = manga.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedElement(
                                            rememberSharedContentState(key = "manga_cover_${manga.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                )
                            }
                        } else {
                            AsyncImage(
                                model = manga.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp, bottom = 4.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = manga.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 28.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = manga.author ?: "Unknown Author",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val (statusIcon, statusLabel, statusColor) = when (manga.status) {
                            1L -> Triple(Icons.Default.Schedule, "Ă„Âang tiĂ¡ÂºÂ¿n hÄ‚Â nh", Color(0xFF4CAF50))
                            2L -> Triple(Icons.Default.CheckCircle, "Ă„ÂÄ‚Â£ hoÄ‚Â n thÄ‚Â nh", Color(0xFF2196F3))
                            else -> Triple(Icons.Default.Help, "KhÄ‚Â´ng rÄ‚Âµ", Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sourceName,
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MangaActionButton(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (isLiked) "Trong thÆ° viá»‡n" else "ThĂªm vĂ o",
                    color = if (isLiked) PrimaryOrange else Color.White,
                    onClick = onFavoriteClick
                )
                MangaActionButton(Icons.Default.Public, "WebView", Color.White, onClick = onWebViewClick)
                MangaActionButton(Icons.Default.TrackChanges, "Tracking", Color.White, onClick = onTrackClick)
                MangaActionButton(Icons.Default.Share, "Share", Color.White, onClick = onShareClick)
            }
        }

        item {
            var isDescriptionExpanded by remember { mutableStateOf(false) }
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = manga.description ?: "No description available.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                )
                if (!isDescriptionExpanded && (manga.description?.length ?: 0) > 150) {
                    Text(
                        "More",
                        color = PrimaryOrange,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp).clickable { isDescriptionExpanded = true }
                    )
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                manga.genre?.forEach { genre ->
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp).clickable { onTagClick(genre) }
                    ) {
                        Text(
                            text = genre,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${chapters.size} chÆ°Æ¡ng",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                IconButton(onClick = onFilterClick) {
                    Icon(Icons.Default.Sort, null, tint = Color.White)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.1f)
            )
        }

        items(chapters) { chapter ->
            ChapterItem(
                chapter = chapter,
                onRead = { onChapterClick(chapter) },
                onDownload = { onDownloadChapter(chapter) },
                isHttpSource = manga.source != 0L
            )
        }
    }
}

@Composable
fun MangaActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = color.copy(alpha = 0.9f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChapterItem(
    chapter: Chapter,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    isHttpSource: Boolean
) {
    Surface(
        onClick = onRead,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val cleanName = com.example.manga_readerver2.core.utils.ChapterRecognition.getDisplayTitle(chapter.name)
                // NĂ¡ÂºÂ¿u tÄ‚Âªn gĂ¡Â»â€˜c cÄ‚Â³ chĂ¡Â»Â©a cÄ‚Â¡c tĂ¡Â»Â« thÄ‚Âªm (nhĂ†Â° tiÄ‚Âªu Ă„â€˜Ă¡Â»Â phĂ¡Â»Â¥), ta cÄ‚Â³ thĂ¡Â»Æ’ hiĂ¡Â»Æ’n thĂ¡Â»â€¹ thÄ‚Âªm, nhĂ†Â°ng 
                // tĂ¡ÂºÂ¡m thĂ¡Â»Âi theo Ă„â€˜Ä‚Âºng logic cĂ¡Â»Â§a manga-reader: lĂ¡ÂºÂ¥y tÄ‚Âªn sĂ¡ÂºÂ¡ch
                // NĂ¡ÂºÂ¿u cleanName quÄ‚Â¡ ngĂ¡ÂºÂ¯n, ta vĂ¡ÂºÂ«n cÄ‚Â³ thĂ¡Â»Æ’ dÄ‚Â¹ng cleanName
                
                Text(
                    text = cleanName.ifEmpty { chapter.name },
                    color = if (chapter.read) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // HiĂ¡Â»Æ’n thĂ¡Â»â€¹ tÄ‚Âªn gĂ¡Â»â€˜c nhĂ¡Â»Â mĂ¡Â»Â Ă¡Â»Å¸ dĂ†Â°Ă¡Â»â€ºi nĂ¡ÂºÂ¿u nÄ‚Â³ khÄ‚Â¡c vĂ¡Â»â€ºi cleanName
                if (cleanName != chapter.name && !chapter.name.equals(cleanName, ignoreCase = true)) {
                    Text(
                        text = chapter.name,
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                val dateStr = try {
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(chapter.dateUpload))
                } catch(e: Exception) { "" }
                Text(
                    text = if (dateStr.isNotEmpty()) dateStr else "Unknown Date",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            // Download Icon: chĂ¡Â»â€° hiĂ¡Â»â€¡n vĂ¡Â»â€ºi HTTP source thĂ¡ÂºÂ­t sĂ¡Â»Â± (khÄ‚Â´ng hiĂ¡Â»â€¡n cho Local hoĂ¡ÂºÂ·c VBook JS)
            if (isHttpSource) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DownloadForOffline,
                        contentDescription = "Download",
                        tint = Color.Gray.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MangaCoverDialog(
    manga: Manga,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                contentScale = ContentScale.Fit
            )
            
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun ChapterSettingsDialog(
    currentSort: com.example.manga_readerver2.features.detail.ChapterSort,
    currentFilter: com.example.manga_readerver2.features.detail.ChapterFilter,
    onDismiss: () -> Unit,
    onSortChange: (com.example.manga_readerver2.features.detail.ChapterSort) -> Unit,
    onFilterChange: (com.example.manga_readerver2.features.detail.ChapterFilter) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CÄ‚Â i Ă„â€˜Ă¡ÂºÂ·t chĂ†Â°Ă†Â¡ng", color = Color.White) },
        text = {
            Column {
                Text("SĂ¡ÂºÂ¯p xĂ¡ÂºÂ¿p theo", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
                Row {
                    FilterChip(
                        selected = currentSort == com.example.manga_readerver2.features.detail.ChapterSort.LATEST,
                        onClick = { onSortChange(com.example.manga_readerver2.features.detail.ChapterSort.LATEST) },
                        label = { Text("MĂ¡Â»â€ºi nhĂ¡ÂºÂ¥t") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = currentSort == com.example.manga_readerver2.features.detail.ChapterSort.OLDEST,
                        onClick = { onSortChange(com.example.manga_readerver2.features.detail.ChapterSort.OLDEST) },
                        label = { Text("CĂ…Â© nhĂ¡ÂºÂ¥t") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("LĂ¡Â»Âc", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
                Row {
                    FilterChip(
                        selected = currentFilter == com.example.manga_readerver2.features.detail.ChapterFilter.ALL,
                        onClick = { onFilterChange(com.example.manga_readerver2.features.detail.ChapterFilter.ALL) },
                        label = { Text("TĂ¡ÂºÂ¥t cĂ¡ÂºÂ£") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = currentFilter == com.example.manga_readerver2.features.detail.ChapterFilter.UNREAD,
                        onClick = { onFilterChange(com.example.manga_readerver2.features.detail.ChapterFilter.UNREAD) },
                        label = { Text("ChĂ†Â°a Ă„â€˜Ă¡Â»Âc") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = currentFilter == com.example.manga_readerver2.features.detail.ChapterFilter.DOWNLOADED,
                        onClick = { onFilterChange(com.example.manga_readerver2.features.detail.ChapterFilter.DOWNLOADED) },
                        label = { Text("Ă„ÂÄ‚Â£ tĂ¡ÂºÂ£i") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Ă„ÂÄ‚Â³ng", color = PrimaryOrange)
            }
        },
        containerColor = Color(0xFF2B2B2B),
        titleContentColor = Color.White
    )
}

@Composable
fun CategorySelectionDialog(
    allCategories: List<Category>,
    selectedIds: List<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    onUnfavorite: () -> Unit
) {
    var currentSelected by remember { mutableStateOf(selectedIds) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Categories") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (allCategories.isEmpty()) {
                    Text("No categories created yet.", color = Color.Gray)
                } else {
                    allCategories.forEach { category ->
                        val isChecked = currentSelected.contains(category.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelected = if (isChecked) {
                                        currentSelected - category.id
                                    } else {
                                        currentSelected + category.id
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                            )
                            Text(
                                text = category.name,
                                color = Color.White,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelected) }) {
                Text("Confirm", color = PrimaryOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onUnfavorite) {
                Text("Unfavorite", color = Color.Red)
            }
        }
    )
}
