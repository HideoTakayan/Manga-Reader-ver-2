package com.example.manga_readerver2.features.reader.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.manga_readerver2.features.reader.ReaderPage
import com.example.manga_readerver2.features.reader.ReaderScreenModel
import com.example.manga_readerver2.features.reader.ReadingMode
import com.example.manga_readerver2.features.reader.PageImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerViewer(
    pages: List<ReaderPage>,
    screenModel: ReaderScreenModel,
    readingMode: ReadingMode,
    scaleMode: Int,
    cropBorders: Boolean,
    bgColor: Color,
    isAutoScrolling: Boolean = false,
    autoScrollSpeed: Float = 1f,
    onTap: (Float, Float, Float) -> Unit,
    onLongPress: ((ReaderPage) -> Unit)? = null
) {
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val dualPageEnabled by screenModel.dualPage.collectAsState()
    val isDualPage = isLandscape && dualPageEnabled && readingMode != ReadingMode.VERTICAL

    fun getPagerCount(): Int = if (isDualPage) (pages.size + 1) / 2 else pages.size
    fun getPagerIndex(pageIndex: Int): Int = if (isDualPage) pageIndex / 2 else pageIndex
    fun getOriginalIndex(pagerIndex: Int): Int = if (isDualPage) pagerIndex * 2 else pagerIndex

    val pagerCount = getPagerCount()
    val totalPagerCount = pagerCount + 1 // +1 for transition

    val pagerState = rememberPagerState(
        initialPage = getPagerIndex(screenModel.currentPage.value).coerceIn(0, totalPagerCount - 1),
        pageCount = { totalPagerCount }
    )
    val scope = rememberCoroutineScope()

    // Auto turn page logic
    LaunchedEffect(isAutoScrolling, autoScrollSpeed, pagerState.currentPage) {
        if (isAutoScrolling) {
            val delayMs = (3000L / autoScrollSpeed).toLong()
            kotlinx.coroutines.delay(delayMs)
            
            if (pagerState.currentPage < pagerCount) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                screenModel.loadNextChapter()
            }
        }
    }

    // Sync screenModel.currentPage -> Pager
    val currentScreenPage by screenModel.currentPage.collectAsState()
    LaunchedEffect(currentScreenPage, isDualPage) {
        val targetPagerIndex = getPagerIndex(currentScreenPage)
        if (targetPagerIndex != pagerState.currentPage && targetPagerIndex < pagerCount) {
            pagerState.scrollToPage(targetPagerIndex)
        }
    }

    // Sync Pager -> screenModel.currentPage
    LaunchedEffect(pagerState, isDualPage) {
        snapshotFlow { pagerState.currentPage }.collect { pIndex ->
            if (pIndex < pagerCount) {
                val originalIndex = getOriginalIndex(pIndex)
                // In dual page, we just set it to the first page of the spread
                if (getPagerIndex(screenModel.currentPage.value) != pIndex) {
                    screenModel.setPage(originalIndex)
                }
            } else {
                screenModel.loadNextChapter()
            }
        }
    }

    val reverseLayout = readingMode == ReadingMode.RIGHT_TO_LEFT

    if (readingMode == ReadingMode.VERTICAL) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().background(bgColor),
            key = { if (it < pagerCount) it else "transition" }
        ) { pIndex ->
            if (pIndex < pagerCount) {
                val pageIndex = getOriginalIndex(pIndex)
                val page = pages[pageIndex]
                LaunchedEffect(pageIndex) { 
                    screenModel.loadPage(pageIndex) 
                    if (pageIndex + 1 < pages.size) screenModel.loadPage(pageIndex + 1)
                    if (pageIndex + 2 < pages.size) screenModel.loadPage(pageIndex + 2)
                }
                PageImage(
                    page = page,
                    screenModel = screenModel,
                    scaleMode = scaleMode,
                    isWebtoon = false,
                    cropBorders = cropBorders,
                    onTap = onTap,
                    onLongPress = onLongPress
                )
            } else {
                com.example.manga_readerver2.features.reader.ChapterTransitionPage(screenModel)
            }
        }
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().background(bgColor),
            reverseLayout = reverseLayout,
            key = { if (it < pagerCount) it else "transition" }
        ) { pIndex ->
            if (pIndex < pagerCount) {
                val pageIndex1 = getOriginalIndex(pIndex)
                val page1 = pages[pageIndex1]
                val page2 = if (isDualPage) pages.getOrNull(pageIndex1 + 1) else null
                
                LaunchedEffect(pageIndex1) { 
                    screenModel.loadPage(pageIndex1)
                    if (page2 != null) screenModel.loadPage(pageIndex1 + 1)
                    if (pageIndex1 + 2 < pages.size) screenModel.loadPage(pageIndex1 + 2)
                    if (pageIndex1 + 3 < pages.size) screenModel.loadPage(pageIndex1 + 3)
                }

                if (isDualPage && page2 != null) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        val modifier = Modifier.weight(1f).fillMaxHeight()
                        if (reverseLayout) {
                            Box(modifier) { PageImage(page2, screenModel, scaleMode, false, cropBorders, onTap, onLongPress) }
                            Box(modifier) { PageImage(page1, screenModel, scaleMode, false, cropBorders, onTap, onLongPress) }
                        } else {
                            Box(modifier) { PageImage(page1, screenModel, scaleMode, false, cropBorders, onTap, onLongPress) }
                            Box(modifier) { PageImage(page2, screenModel, scaleMode, false, cropBorders, onTap, onLongPress) }
                        }
                    }
                } else {
                    PageImage(
                        page = page1,
                        screenModel = screenModel,
                        scaleMode = scaleMode,
                        isWebtoon = false,
                        cropBorders = cropBorders,
                        onTap = onTap,
                        onLongPress = onLongPress
                    )
                }
            } else {
                com.example.manga_readerver2.features.reader.ChapterTransitionPage(screenModel)
            }
        }
    }
}
