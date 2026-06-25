package com.example.manga_readerver2.features.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.download.Download
import com.example.manga_readerver2.core.download.DownloadManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DownloadQueueScreen : Screen {
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val downloadManager: DownloadManager = Injekt.get()
        val queue by downloadManager.queueState.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("HĂ ng Ä‘á»£i táº£i xuá»‘ng", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay láº¡i", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { downloadManager.retryAllFailed() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Táº£i láº¡i cĂ¡c má»¥c lá»—i", tint = Color.White)
                        }
                        IconButton(onClick = { downloadManager.startDownloads() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Tiáº¿p tá»¥c", tint = Color.White)
                        }
                        IconButton(onClick = { downloadManager.pauseDownloads() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Táº¡m dá»«ng", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
                )
            },
            containerColor = Color(0xFF121212)
        ) { paddingValues ->
            if (queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("KhĂ´ng cĂ³ tiáº¿n trĂ¬nh táº£i nĂ o", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(queue) { download ->
                        DownloadItemRow(download, downloadManager)
                    }
                }
            }
        }
    }

    @Composable
    private fun DownloadItemRow(download: Download, manager: DownloadManager) {
        val progress = download.progressFlow.collectAsState().value
        val status = download.statusFlow.collectAsState().value

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.manga.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = download.chapter.name,
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                val statusText = when (status) {
                    Download.State.QUEUE -> "Äang chá»"
                    Download.State.DOWNLOADING -> "Äang táº£i... $progress%"
                    Download.State.COMPRESSING -> "Äang nĂ©n file"
                    Download.State.DOWNLOADED -> "HoĂ n táº¥t"
                    Download.State.ERROR -> "Lá»—i"
                    else -> ""
                }
                Text(text = statusText, color = Color(0xFFFF6D00), fontSize = 12.sp)
                
                if (status == Download.State.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = Color(0xFFFF6D00)
                    )
                }
            }
            
            IconButton(onClick = { manager.cancelDownload(download) }) {
                Icon(Icons.Default.Cancel, contentDescription = "Há»§y", tint = Color.Gray)
            }
        }
    }
}


