package com.example.manga_readerver2.features.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.network.httpHeaders
import coil3.network.NetworkHeaders
import com.example.manga_readerver2.core.utils.ReaderBlock
import com.example.manga_readerver2.features.reader.ReaderScreenModel
import com.example.manga_readerver2.ui.theme.PrimaryOrange

data class BlockItem(
    val block: ReaderBlock,
    val blockIndex: Int,
    val ttsIndex: Int // -1 if not a Text block
)

@Composable
fun TextReaderViewer(
    blocks: List<ReaderBlock>,
    screenModel: ReaderScreenModel,
    listState: LazyListState = rememberLazyListState(),
    textColor: Color = Color.White,
    onTap: ((Float, Float, Float, Float) -> Unit)? = null
) {
    val currentTtsParagraph by screenModel.currentTtsParagraph.collectAsState()
    val fontSizePercent by screenModel.fontSize.collectAsState()
    val lineSpacing by screenModel.lineSpacing.collectAsState()
    
    val context = LocalContext.current
    val sourceHeaders = remember(screenModel) { screenModel.getSourceHeaders() }

    val blockItems = remember(blocks) {
        var textIndex = 0
        blocks.mapIndexed { i, block ->
            if (block is ReaderBlock.Text) {
                BlockItem(block, i, textIndex++)
            } else {
                BlockItem(block, i, -1)
            }
        }
    }

    LaunchedEffect(blocks) {
        val textCount = blocks.count { it is ReaderBlock.Text }
        screenModel.setTextPageCount(textCount)
    }

    // Auto-scroll to active TTS paragraph
    LaunchedEffect(currentTtsParagraph) {
        if (currentTtsParagraph >= 0) {
            val targetBlockIndex = blockItems.indexOfFirst { it.ttsIndex == currentTtsParagraph }
            if (targetBlockIndex >= 0) {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val isVisible = visibleItems.any { it.index == targetBlockIndex }
                if (!isVisible) {
                    listState.animateScrollToItem(targetBlockIndex)
                }
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                onTap?.invoke(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
            }
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            itemsIndexed(blockItems) { index, item ->
                when (val block = item.block) {
                    is ReaderBlock.Text -> {
                        val isActive = item.ttsIndex == currentTtsParagraph
                        val backgroundColor = if (isActive) PrimaryOrange.copy(alpha = 0.15f) else Color.Transparent

                            Text(
                                text = block.text,
                                color = textColor,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                fontSize = (18f * (fontSizePercent / 100f)).sp,
                                lineHeight = (18f * (fontSizePercent / 100f) * lineSpacing).sp,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp)
                                .padding(bottom = 16.dp)
                        )
                    }
                    is ReaderBlock.Image -> {
                        val imageRequest = remember(block.url) {
                            ImageRequest.Builder(context)
                                .data(block.url)
                                .apply {
                                    if (sourceHeaders != null) {
                                        val builder = NetworkHeaders.Builder()
                                        sourceHeaders.forEach { (key, value) -> 
                                            builder.set(key, value) 
                                        }
                                        httpHeaders(builder.build())
                                    }
                                }
                                .build()
                        }
                        
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = block.altText.takeIf { it.isNotBlank() } ?: "Ảnh minh họa",
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 200.dp)
                                .wrapContentHeight()
                                .padding(vertical = 12.dp),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}
