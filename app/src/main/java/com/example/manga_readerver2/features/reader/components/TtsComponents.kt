package com.example.manga_readerver2.features.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manga_readerver2.features.reader.ReaderScreenModel
import com.example.manga_readerver2.ui.theme.PrimaryOrange

@Composable
fun TtsPlayerBar(
    screenModel: ReaderScreenModel,
    onShowSettings: () -> Unit,
    onClose: () -> Unit
) {
    val isPlaying by screenModel.isTtsPlaying.collectAsState()
    
    Surface(
        color = Color.Black.copy(alpha = 0.9f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Äang Ä‘á»c ná»™i dung...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onShowSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { screenModel.ttsRewind() }) {
                    Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                FloatingActionButton(
                    onClick = { if (isPlaying) screenModel.pauseTts() else screenModel.startTts() },
                    containerColor = PrimaryOrange,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                IconButton(onClick = { screenModel.ttsForward() }) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TtsSettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderScreenModel
) {
    val currentSpeed by screenModel.ttsSpeed.collectAsState()
    val currentPitch by screenModel.ttsPitch.collectAsState()
    val availableVoices by screenModel.availableVoices.collectAsState()
    val selectedVoice by screenModel.selectedVoice.collectAsState()
    
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("CĂ i Ä‘áº·t Giá»ng Äá»c", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (availableVoices.isNotEmpty()) {
                    Text("Giá»ng Ä‘á»c", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedVoice?.name?.substringAfterLast("-") ?: "Máº·c Ä‘á»‹nh",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedBorderColor = PrimaryOrange,
                                focusedLabelColor = PrimaryOrange
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableVoices.forEach { voice ->
                                val displayName = "${voice.locale.displayName} - ${voice.name.substringAfterLast("-")}"
                                DropdownMenuItem(
                                    text = { Text(displayName) },
                                    onClick = {
                                        screenModel.setTtsVoice(voice)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("Tá»‘c Ä‘á»™ Ä‘á»c: ${(currentSpeed * 100).toInt()}%", fontSize = 14.sp)
                Slider(
                    value = currentSpeed,
                    onValueChange = { speed: Float -> screenModel.updateTtsSpeed(speed) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Äá»™ cao giá»ng: ${(currentPitch * 100).toInt()}%", fontSize = 14.sp)
                Slider(
                    value = currentPitch,
                    onValueChange = { pitch: Float -> screenModel.updateTtsPitch(pitch) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Xong", color = PrimaryOrange)
            }
        }
    )
}

