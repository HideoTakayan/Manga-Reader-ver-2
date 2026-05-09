@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.manga_readerver2.features.reader.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manga_readerver2.features.reader.ReadingMode
import com.example.manga_readerver2.features.reader.ReaderScreenModel
import com.example.manga_readerver2.ui.components.*
import com.example.manga_readerver2.ui.theme.*
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Check

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    onDismissRequest: () -> Unit,
    isTextReader: Boolean,
    screenModel: ReaderScreenModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = if (isTextReader) {
        listOf("Kiểu đọc", "Hiển thị", "TTS")
    } else {
        listOf("Kiểu đọc", "Chung", "Bộ lọc")
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = CardBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = PrimaryOrange,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontSize = 14.sp) },
                        unselectedContentColor = TextSecondary
                    )
                }
            }
            
            HorizontalDivider(color = GlassBorder)

            // Brightness Control (VBook style)
            val brightness by screenModel.brightness.collectAsState()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Slider(
                        value = brightness,
                        onValueChange = { screenModel.setBrightness(it) },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                    )
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 500.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> ReadingModeTab(isTextReader, screenModel)
                    1 -> if (isTextReader) TextDisplayTab(screenModel) else GeneralSettingsTab(isTextReader, screenModel)
                    2 -> if (isTextReader) TtsSettingsTab(screenModel) else FilterSettingsTab(screenModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingModeTab(isTextReader: Boolean, screenModel: ReaderScreenModel) {
    val readingMode by screenModel.readingMode.collectAsState()
    val orientation by screenModel.orientation.collectAsState()
    
    Column {
        HeadingItem("Dành cho phần truyện này")
        HeadingItem("Kiểu đọc")
        if (!isTextReader) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingModeButton("Đọc ngang", readingMode == ReadingMode.HORIZONTAL) {
                    screenModel.setReadingMode(ReadingMode.HORIZONTAL)
                }
                ReadingModeButton("Đọc dọc", readingMode == ReadingMode.VERTICAL) {
                    screenModel.setReadingMode(ReadingMode.VERTICAL)
                }
            }
        } else {
            ReadingModeButton("Cuộn liên tục", true) {}
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        HeadingItem("Hướng xoay")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadingModeButton("Mặc định", orientation == 0) { screenModel.setOrientation(0) }
            ReadingModeButton("Khóa dọc", orientation == 1) { screenModel.setOrientation(1) }
            ReadingModeButton("Khóa ngang", orientation == 2) { screenModel.setOrientation(2) }
        }
    }
}

@Composable
private fun ReadingModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PrimaryOrange else Color.White.copy(alpha = 0.1f),
            contentColor = if (isSelected) Color.White else TextPrimary
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun TextDisplayTab(screenModel: ReaderScreenModel) {
    val fontSize by screenModel.fontSize.collectAsState()
    val lineSpacing by screenModel.lineSpacing.collectAsState()
    val readerTheme by screenModel.readerTheme.collectAsState()

    Column {
        HeadingItem("Màu nền")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeCircle("Đen", Color.Black, readerTheme == 0) { screenModel.setReaderTheme(0) }
            ThemeCircle("Trắng", Color.White, readerTheme == 1) { screenModel.setReaderTheme(1) }
            ThemeCircle("Sepia", Color(0xFFF4ECD8), readerTheme == 2) { screenModel.setReaderTheme(2) }
            ThemeCircle("Xanh", Color(0xFFE3F2FD), readerTheme == 3) { screenModel.setReaderTheme(3) }
            ThemeCircle("Hồng", Color(0xFFFCE4EC), readerTheme == 4) { screenModel.setReaderTheme(4) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Precise Font Size Control (VBook style)
        PreciseSettingItem(
            label = "Cỡ chữ",
            value = "${fontSize.toInt()}%",
            onDecrement = { screenModel.setFontSize(fontSize - 1f) },
            onIncrement = { screenModel.setFontSize(fontSize + 1f) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Precise Line Spacing Control (VBook style)
        PreciseSettingItem(
            label = "Dẫn dòng",
            value = "${(lineSpacing * 100).toInt()}%",
            onDecrement = { screenModel.setLineSpacing(lineSpacing - 0.05f) },
            onIncrement = { screenModel.setLineSpacing(lineSpacing + 0.05f) }
        )
    }
}

@Composable
fun PreciseSettingItem(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(80.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onDecrement) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = PrimaryOrange)
            }
            Text(
                value,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(60.dp)
            )
            IconButton(onClick = onIncrement) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = PrimaryOrange)
            }
        }
        
        Surface(
            onClick = { /* TODO: Custom Add */ },
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Text(
                "Thêm",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ThemeCircle(label: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = color,
            border = BorderStroke(2.dp, if (isSelected) PrimaryOrange else Color.White.copy(alpha = 0.1f)),
            onClick = onClick
        ) {
            if (isSelected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = if (color == Color.White) Color.Black else Color.White)
                }
            }
        }
        Text(label, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TtsSettingsTab(screenModel: ReaderScreenModel) {
    val speed by screenModel.ttsSpeed.collectAsState()
    val pitch by screenModel.ttsPitch.collectAsState()
    // Local state cho preview slider khi kéo (không restart TTS mỗi frame)
    var previewSpeed by remember(speed) { mutableStateOf(speed) }
    var previewPitch by remember(pitch) { mutableStateOf(pitch) }
    Column {
        HeadingItem("Giọng đọc")
        Text("Tốc độ: ${(previewSpeed * 100).toInt()}%", fontSize = 14.sp, color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Slider(
            value = previewSpeed,
            onValueChange = { previewSpeed = it }, // Chỉ cập nhật UI, không restart TTS
            onValueChangeFinished = { screenModel.updateTtsSpeed(previewSpeed) }, // Restart khi thả tay
            valueRange = 0.5f..2.0f,
            modifier = Modifier.padding(horizontal = 16.dp),
            colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Độ cao giọng: ${(previewPitch * 100).toInt()}%", fontSize = 14.sp, color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Slider(
            value = previewPitch,
            onValueChange = { previewPitch = it }, // Chỉ cập nhật UI
            onValueChangeFinished = { screenModel.updateTtsPitch(previewPitch) }, // Restart khi thả tay
            valueRange = 0.5f..2.0f,
            modifier = Modifier.padding(horizontal = 16.dp),
            colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
        )
    }
}

@Composable
private fun GeneralSettingsTab(isTextReader: Boolean, screenModel: ReaderScreenModel) {
    val keepScreenOn by screenModel.keepScreenOn.collectAsState()
    val fullscreen by screenModel.fullscreen.collectAsState()
    val incognitoMode by screenModel.incognitoMode.collectAsState()
    
    Column {
        HeadingItem("Cài đặt chung")
        CheckboxItem(
            label = "Giữ màn hình luôn sáng", 
            checked = keepScreenOn, 
            onClick = { screenModel.setKeepScreenOn(!keepScreenOn) }
        )
        CheckboxItem(
            label = "Chế độ toàn màn hình", 
            checked = fullscreen, 
            onClick = { screenModel.setFullscreen(!fullscreen) }
        )
        CheckboxItem(
            label = "Chế độ ẩn danh (Không lưu lịch sử)", 
            checked = incognitoMode, 
            onClick = { screenModel.setIncognitoMode(!incognitoMode) }
        )

        if (!isTextReader) {
            val cropBorders by screenModel.cropBorders.collectAsState()
            CheckboxItem(
                label = "Cắt viền ảnh", 
                checked = cropBorders, 
                onClick = { screenModel.setCropBorders(!cropBorders) }
            )
        }

        val autoDownloadAmount = screenModel.autoDownloadAmount.collectAsState().value
        SliderItem(
            label = "Tự động tải trước", 
            value = autoDownloadAmount.toFloat(), 
            valueRange = 0f..10f, 
            valueString = if (autoDownloadAmount > 0) "$autoDownloadAmount chương" else "Tắt", 
            onValueChange = { screenModel.setAutoDownloadAmount(it.toInt()) }
        )

        if (!isTextReader) {
            Spacer(modifier = Modifier.height(16.dp))
            HeadingItem("Chế độ thu phóng")
            val scaleMode by screenModel.scaleMode.collectAsState()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingModeButton("Vừa màn hình", scaleMode == 0) { screenModel.setScaleMode(0) }
                ReadingModeButton("Vừa chiều rộng", scaleMode == 1) { screenModel.setScaleMode(1) }
                ReadingModeButton("Vừa chiều cao", scaleMode == 2) { screenModel.setScaleMode(2) }
            }
        }
    }
}

@Composable
private fun FilterSettingsTab(screenModel: ReaderScreenModel) {
    val filterMode by screenModel.colorFilterMode.collectAsState()
    
    Column {
        HeadingItem("Bộ lọc tùy chỉnh")
        CheckboxItem(label = "Không có", checked = filterMode == 0, onClick = { screenModel.setColorFilterMode(0) })
        CheckboxItem(label = "Xám (Grayscale)", checked = filterMode == 1, onClick = { screenModel.setColorFilterMode(1) })
        CheckboxItem(label = "Đảo ngược màu", checked = filterMode == 2, onClick = { screenModel.setColorFilterMode(2) })
        CheckboxItem(label = "Sepia", checked = filterMode == 3, onClick = { screenModel.setColorFilterMode(3) })
    }
}

@Composable
private fun ColorCircle(color: Color, isSelected: Boolean) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = color,
        border = if (isSelected) BorderStroke(2.dp, PrimaryOrange) else null
    ) {}
}
