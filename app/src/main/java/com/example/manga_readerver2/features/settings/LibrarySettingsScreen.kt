package com.example.manga_readerver2.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.preference.LibraryPreferences
import com.example.manga_readerver2.core.updater.LibraryUpdateJob
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibrarySettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        
        var updateInterval by remember { mutableStateOf(libraryPreferences.updateInterval.get()) }
        var wifiOnly by remember { mutableStateOf(libraryPreferences.updateWifiOnly.get()) }

        // Má»Ÿ Dialog chá»n Táº§n suáº¥t
        var showIntervalDialog by remember { mutableStateOf(false) }

        val intervalOptions = listOf(
            0 to "Thá»§ cĂ´ng",
            12 to "Má»—i 12 giá»",
            24 to "Má»—i 24 giá»",
            48 to "Má»—i 48 giá»"
        )

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("ThÆ° viá»‡n", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay láº¡i", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsSectionHeader(title = "Cáº­p nháº­t ngáº§m")

                // Chá»n Táº§n suáº¥t
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showIntervalDialog = true },
                    color = Color.Transparent
                ) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Text("Táº§n suáº¥t cáº­p nháº­t", color = Color.White, fontSize = 16.sp)
                        val currentText = intervalOptions.find { it.first == updateInterval }?.second ?: "Thá»§ cĂ´ng"
                        Text(currentText, color = Color.Gray, fontSize = 13.sp)
                    }
                }

                // Switch WiFi Only
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (updateInterval != 0) {
                            wifiOnly = !wifiOnly
                            libraryPreferences.updateWifiOnly.set(wifiOnly)
                            LibraryUpdateJob.setupTask(context, updateInterval, wifiOnly)
                        }
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chá»‰ cáº­p nháº­t khi dĂ¹ng Wi-Fi", color = if (updateInterval == 0) Color.DarkGray else Color.White, fontSize = 16.sp)
                            Text("Tiáº¿t kiá»‡m dá»¯ liá»‡u máº¡ng di Ä‘á»™ng", color = Color.Gray, fontSize = 13.sp)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { 
                                wifiOnly = it
                                libraryPreferences.updateWifiOnly.set(it)
                                LibraryUpdateJob.setupTask(context, updateInterval, it)
                            },
                            enabled = updateInterval != 0,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryOrange,
                                checkedTrackColor = PrimaryOrange.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }

        if (showIntervalDialog) {
            AlertDialog(
                onDismissRequest = { showIntervalDialog = false },
                title = { Text("Táº§n suáº¥t cáº­p nháº­t") },
                text = {
                    Column {
                        intervalOptions.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        updateInterval = value
                                        libraryPreferences.updateInterval.set(value)
                                        LibraryUpdateJob.setupTask(context, value, wifiOnly)
                                        showIntervalDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = updateInterval == value,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryOrange)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(label, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {},
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White
            )
        }
    }
}


