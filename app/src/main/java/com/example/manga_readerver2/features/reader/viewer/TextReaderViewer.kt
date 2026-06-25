package com.example.manga_readerver2.features.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import com.example.manga_readerver2.features.reader.ReaderScreenModel
import com.example.manga_readerver2.ui.theme.PrimaryOrange

@Composable
fun TextReaderViewer(
    content: String,
    screenModel: ReaderScreenModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    textColor: Color,
    onTap: (Float, Float, Float) -> Unit
) {
    val paragraphs = remember(content) { content.split("\n").filter { it.isNotBlank() } }
    val currentTtsParagraph by screenModel.currentTtsParagraph.collectAsState()
    
    val fontSizePercent by screenModel.fontSize.collectAsState()
    val lineSpacing by screenModel.lineSpacing.collectAsState()

    LaunchedEffect(paragraphs.size) {
        screenModel.setTextPageCount(paragraphs.size)
    }

    // Auto-scroll to active TTS paragraph
    LaunchedEffect(currentTtsParagraph) {
        if (currentTtsParagraph >= 0) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isVisible = visibleItems.any { it.index == currentTtsParagraph }
            if (!isVisible) {
                listState.animateScrollToItem(currentTtsParagraph)
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                onTap(offset.x, offset.y, size.width.toFloat())
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
            itemsIndexed(paragraphs) { index, paragraph ->
                val isActive = index == currentTtsParagraph
                val backgroundColor = if (isActive) PrimaryOrange.copy(alpha = 0.15f) else Color.Transparent

                Surface(
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = paragraph,
                        color = textColor,
                        fontSize = (18f * (fontSizePercent / 100f)).sp,
                        lineHeight = (18f * (fontSizePercent / 100f) * lineSpacing).sp,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
