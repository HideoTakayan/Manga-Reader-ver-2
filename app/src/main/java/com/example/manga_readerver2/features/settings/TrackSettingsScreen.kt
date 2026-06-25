package com.example.manga_readerver2.features.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.track.AniListManager
import com.example.manga_readerver2.core.track.TrackPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        
        val anilistToken by trackPreferences.anilistToken.asFlow().collectAsState(initial = trackPreferences.anilistToken.get())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Theo dõi tiến độ") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Đồng bộ tiến độ đọc với các dịch vụ theo dõi anime/manga.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "AniList",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("AniList", style = MaterialTheme.typography.titleMedium)
                                if (anilistToken.isNotEmpty()) {
                                    Text("Đã đăng nhập", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text("Chưa đăng nhập", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        if (anilistToken.isNotEmpty()) {
                            OutlinedButton(onClick = { trackPreferences.anilistToken.set("") }) {
                                Text("Đăng xuất")
                            }
                        } else {
                            Button(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AniListManager.AUTH_URL))
                                context.startActivity(intent)
                            }) {
                                Text("Đăng nhập")
                            }
                        }
                    }
                }
            }
        }
    }
}
