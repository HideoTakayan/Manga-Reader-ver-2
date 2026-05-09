package com.example.manga_readerver2.features.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.domain.repository.ExtensionRepo
import com.example.manga_readerver2.domain.repository.ExtensionRepoRepository
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionRepoScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ExtensionRepoScreenModel() }
        val repos by screenModel.repos.collectAsState()
        
        var showDialog by remember { mutableStateOf(false) }
        var repoUrl by remember { mutableStateOf("") }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Quản lý nguồn", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showDialog = true },
                    containerColor = PrimaryOrange,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm nguồn")
                }
            }
        ) { paddingValues ->
            if (repos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chưa có nguồn nào. Nhấn + để thêm.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(paddingValues)) {
                    items(repos) { repo ->
                        ListItem(
                            headlineContent = { Text(repo.name, color = Color.White) },
                            supportingContent = { Text(repo.baseUrl, color = Color.Gray) },
                            trailingContent = {
                                IconButton(onClick = { screenModel.deleteRepo(repo.baseUrl) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Thêm kho lưu trữ") },
                    text = {
                        Column {
                            Text("Nhập URL đến file index.min.json")
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = repoUrl,
                                onValueChange = { repoUrl = it },
                                placeholder = { Text("https://example.com/repo") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (repoUrl.isNotBlank()) {
                                screenModel.addRepo(repoUrl)
                                repoUrl = ""
                                showDialog = false
                            }
                        }) {
                            Text("Thêm", color = PrimaryOrange)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Hủy")
                        }
                    }
                )
            }
        }
    }
}

class ExtensionRepoScreenModel(
    private val repository: ExtensionRepoRepository = Injekt.get(),
    private val extensionApi: com.example.manga_readerver2.core.source.ExtensionApi = Injekt.get()
) : ScreenModel {

    val repos: StateFlow<List<ExtensionRepo>> = repository.subscribeAll()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRepo(url: String) {
        screenModelScope.launch {
            // Chuẩn hóa URL
            val baseUrl = url.removeSuffix("/").removeSuffix("/index.min.json").removeSuffix("/plugin.json").removeSuffix("/repo.json")
            
            // Thử lấy metadata thực tế
            val metadata = extensionApi.fetchRepoDetails(baseUrl)
            
            repository.upsertRepo(
                baseUrl = baseUrl,
                name = metadata?.name ?: baseUrl.substringAfterLast("/"),
                shortName = null,
                website = baseUrl,
                signingKeyFingerprint = metadata?.fingerprint ?: ""
            )
        }
    }

    fun deleteRepo(baseUrl: String) {
        screenModelScope.launch {
            repository.deleteRepo(baseUrl)
        }
    }
}
