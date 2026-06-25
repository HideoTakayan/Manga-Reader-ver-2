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

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        Scaffold(
            containerColor = BackgroundDark,
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                var isProcessing by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { if (!isProcessing) showDialog = false },
                    title = { Text("Thêm kho lưu trữ") },
                    text = {
                        Column {
                            Text("Nhập URL đến file index.min.json hoặc repo.json")
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = repoUrl,
                                onValueChange = { repoUrl = it },
                                placeholder = { Text("https://example.com/repo") },
                                singleLine = true,
                                enabled = !isProcessing
                            )
                            if (isProcessing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryOrange)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !isProcessing,
                            onClick = {
                            if (repoUrl.isNotBlank()) {
                                isProcessing = true
                                scope.launch {
                                    val success = screenModel.addRepo(repoUrl)
                                    isProcessing = false
                                    if (success) {
                                        repoUrl = ""
                                        showDialog = false
                                    } else {
                                        snackbarHostState.showSnackbar("Không thể lấy thông tin nguồn. Vui lòng kiểm tra lại URL.")
                                    }
                                }
                            }
                        }) {
                            Text("Thêm", color = PrimaryOrange)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !isProcessing,
                            onClick = { showDialog = false }
                        ) {
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
    private val repoService: com.example.manga_readerver2.core.source.ExtensionRepoService = Injekt.get()
) : ScreenModel {

    val repos: StateFlow<List<ExtensionRepo>> = repository.subscribeAll()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun addRepo(url: String): Boolean {
        // Chuẩn hóa URL
        val baseUrl = url.removeSuffix("/")
            .removeSuffix("/index.min.json")
            .removeSuffix("/repo.json")
            .removeSuffix("/plugin.json")
        
        val details = repoService.fetchRepoDetails(baseUrl)
        if (details != null) {
            repository.upsertRepo(
                baseUrl = details.baseUrl,
                name = details.name,
                shortName = details.shortName,
                website = details.website,
                signingKeyFingerprint = details.signingKeyFingerprint
            )
            return true
        } else {
            // Fallback nếu không lấy được repo.json nhưng vẫn thử thêm
            // Ở đây Mihon thường yêu cầu repo.json phải tồn tại để lấy signing key
            // Nhưng để linh hoạt chúng ta cho phép thêm và đặt tên theo URL
            repository.upsertRepo(
                baseUrl = baseUrl,
                name = baseUrl.substringAfterLast("/"),
                shortName = null,
                website = baseUrl,
                signingKeyFingerprint = ""
            )
            return true
        }
    }

    fun deleteRepo(baseUrl: String) {
        screenModelScope.launch {
            repository.deleteRepo(baseUrl)
        }
    }
}
