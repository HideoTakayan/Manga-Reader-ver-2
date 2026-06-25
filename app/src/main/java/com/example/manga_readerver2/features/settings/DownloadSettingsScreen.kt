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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.preference.DownloadPreferences
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DownloadSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }

        var wifiOnly by remember { mutableStateOf(downloadPreferences.downloadOnlyOverWifi.get()) }
        var autoDelete by remember { mutableStateOf(downloadPreferences.autoDeleteAfterReading.get()) }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Táº£i xuá»‘ng", color = Color.White, fontWeight = FontWeight.Bold) },
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
                SettingsSectionHeader(title = "Máº¡ng")
                
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        wifiOnly = !wifiOnly
                        downloadPreferences.downloadOnlyOverWifi.set(wifiOnly)
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chá»‰ táº£i khi dĂ¹ng Wi-Fi", color = Color.White, fontSize = 16.sp)
                            Text("Táº¡m dá»«ng táº£i khi dĂ¹ng 3G/4G", color = Color.Gray, fontSize = 13.sp)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { 
                                wifiOnly = it
                                downloadPreferences.downloadOnlyOverWifi.set(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryOrange,
                                checkedTrackColor = PrimaryOrange.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                SettingsSectionHeader(title = "LÆ°u trá»¯")
                
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        autoDelete = !autoDelete
                        downloadPreferences.autoDeleteAfterReading.set(autoDelete)
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tá»± Ä‘á»™ng xĂ³a sau khi Ä‘á»c", color = Color.White, fontSize = 16.sp)
                            Text("XĂ³a chÆ°Æ¡ng Ä‘Ă£ táº£i sau khi báº¡n Ä‘á»c Ä‘áº¿n trang cuá»‘i", color = Color.Gray, fontSize = 13.sp)
                        }
                        Switch(
                            checked = autoDelete,
                            onCheckedChange = { 
                                autoDelete = it
                                downloadPreferences.autoDeleteAfterReading.set(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryOrange,
                                checkedTrackColor = PrimaryOrange.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}


